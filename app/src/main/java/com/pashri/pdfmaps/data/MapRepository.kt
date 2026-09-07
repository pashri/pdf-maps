package com.pashri.pdfmaps.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pashri.pdfmaps.render.PdfDocumentSource
import com.pashri.pdfmaps.render.ThumbnailGenerator
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Raised when the app is not allowed to read the shared URI, as
 * distinct from the PDF itself being unreadable.
 *
 * @param cause The underlying permission failure.
 */
private class ImportDenied(cause: SecurityException) :
    Exception(cause)

/** Fallback name when a source URI exposes no filename. */
private const val UNTITLED = "Untitled map"

/**
 * The single entry point to the map library: reads, edits, imports
 * and deletes.
 *
 * @param context Any context; only the application context is kept.
 * @param dao Library DAO.
 * @param files Backing file storage.
 */
class MapRepository(
    context: Context,
    private val dao: MapDao,
    private val files: MapFileStore,
) {

    private val appContext = context.applicationContext

    /**
     * Observes the library in display order.
     *
     * @return A flow of all entries, starred first then alphabetical.
     */
    fun observeAll(): Flow<List<MapEntry>> = dao.observeAll()

    /**
     * Observes one entry.
     *
     * @param id Id of the entry.
     * @return A flow emitting the entry, or null once deleted.
     */
    fun observe(id: String): Flow<MapEntry?> = dao.observeById(id)

    /**
     * Locates the PDF backing an entry.
     *
     * @param entry Entry to resolve.
     * @return The stored PDF file.
     */
    fun pdfFile(entry: MapEntry): File = files.pdfFile(entry.documentId)

    /**
     * Locates the thumbnail for an entry.
     *
     * @param entry Entry to resolve.
     * @return The thumbnail file, which may not exist.
     */
    fun thumbFile(entry: MapEntry): File = files.thumbFile(entry.id)

    /**
     * Imports a PDF, creating one library entry per page.
     *
     * The file is copied into app-private storage first, so the
     * import survives the source being moved or deleted. A PDF that
     * cannot be opened leaves nothing behind.
     *
     * @param uri Content URI of the PDF to import.
     * @return Success with the created entry count, or a failure
     *   carrying a message for the user.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()
        val target = files.pdfFile(documentId)
        val originalName = displayNameOf(uri)

        val created = mutableListOf<String>()
        try {
            // Copying and parsing both raise SecurityException, for
            // very different reasons, so they are caught separately:
            // a revoked share grant must not be reported as an
            // encrypted PDF.
            try {
                copyIn(uri, target)
            } catch (error: SecurityException) {
                throw ImportDenied(error)
            }
            val entries =
                buildEntries(documentId, target, originalName, created)
            dao.insertAll(entries)
            ImportResult.Success(entries.first().displayName, entries.size)
        } catch (error: ImportDenied) {
            discard(documentId, created)
            ImportResult.Failure(
                "No longer allowed to read that file. Try sharing it " +
                    "again.",
            )
        } catch (error: SecurityException) {
            // PdfRenderer reports an encrypted document this way.
            discard(documentId, created)
            ImportResult.Failure("That PDF is password-protected.")
        } catch (error: IOException) {
            discard(documentId, created)
            ImportResult.Failure(
                "Could not read that PDF. It may be damaged or not a " +
                    "PDF at all.",
            )
        }
    }

    /**
     * Copies the source stream into app-private storage.
     *
     * @param uri Source content URI.
     * @param target Destination file.
     * @throws IOException If the URI cannot be opened or read.
     */
    private fun copyIn(uri: Uri, target: File) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
        input.use { source ->
            target.outputStream().use { source.copyTo(it) }
        }
    }

    /**
     * Renders a thumbnail per page and builds the rows to insert.
     *
     * @param documentId Id of the stored PDF.
     * @param file The stored PDF.
     * @param originalName Filename as imported.
     * @param created Collects the ids of every entry whose thumbnail
     *   has been written, so a later failure can clean them up.
     * @return One entry per page, in page order.
     * @throws IOException If the PDF cannot be opened or rendered.
     */
    private suspend fun buildEntries(
        documentId: String,
        file: File,
        originalName: String,
        created: MutableList<String>,
    ): List<MapEntry> = PdfDocumentSource(file).use { source ->
        val pageCount = source.pageCount()
        val stem = originalName.substringBeforeLast('.', originalName)

        (0 until pageCount).map { pageIndex ->
            val entryId = UUID.randomUUID().toString()
            created += entryId
            ThumbnailGenerator.writeThumbnail(
                source = source,
                pageIndex = pageIndex,
                target = files.thumbFile(entryId),
            )
            MapEntry(
                id = entryId,
                documentId = documentId,
                pageIndex = pageIndex,
                pageCount = pageCount,
                displayName = pageName(stem, pageIndex, pageCount),
                originalFileName = originalName,
            )
        }
    }

    /**
     * Names one page: bare stem for a single-page PDF, and a
     * `(i/N)` suffix when the PDF has several pages.
     *
     * @param stem Filename without its extension.
     * @param pageIndex Zero-based page index.
     * @param pageCount Total pages in the PDF.
     * @return The display name for that page.
     */
    private fun pageName(
        stem: String,
        pageIndex: Int,
        pageCount: Int,
    ): String =
        if (pageCount <= 1) stem
        else "$stem (${pageIndex + 1}/$pageCount)"

    /**
     * Removes every file written during a failed import, so a
     * rejected PDF leaves nothing behind on disk.
     *
     * @param documentId Id of the abandoned document.
     * @param entryIds Ids of any thumbnails already written.
     */
    private fun discard(documentId: String, entryIds: List<String>) {
        files.deletePdf(documentId)
        entryIds.forEach(files::deleteThumb)
    }

    /**
     * Reads the human-readable filename behind a content URI.
     *
     * @param uri URI being imported.
     * @return The filename, or a generic fallback.
     */
    private fun displayNameOf(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver
            .query(uri, projection, null, null, null)
            ?.use { cursor ->
                val column =
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) {
                    cursor.getString(column)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: UNTITLED
    }

    /**
     * Renames an entry.
     *
     * @param id Id of the entry.
     * @param name New display name; blank names are ignored.
     */
    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.rename(id, trimmed)
    }

    /**
     * Stars or unstars an entry.
     *
     * @param id Id of the entry.
     * @param starred True to pin it to the top of the library.
     */
    suspend fun setStarred(id: String, starred: Boolean) {
        dao.setStarred(id, starred)
    }

    /**
     * Saves the viewport of an entry so it reopens in place.
     *
     * @param id Id of the entry.
     * @param scale Current zoom factor.
     * @param centerX Viewport centre X, in page coordinates.
     * @param centerY Viewport centre Y, in page coordinates.
     */
    suspend fun saveViewport(
        id: String,
        scale: Float,
        centerX: Float,
        centerY: Float,
    ) {
        dao.saveViewport(id, scale, centerX, centerY)
    }

    /**
     * Deletes an entry, its thumbnail, and the shared PDF once no
     * sibling pages still reference it.
     *
     * @param entry Entry to delete.
     */
    suspend fun delete(entry: MapEntry) = withContext(Dispatchers.IO) {
        dao.deleteById(entry.id)
        files.deleteThumb(entry.id)
        if (dao.countForDocument(entry.documentId) == 0) {
            files.deletePdf(entry.documentId)
        }
    }
}
