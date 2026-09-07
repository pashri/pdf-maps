package com.pashri.pdfmaps.data

import android.content.Context
import java.io.File

/**
 * Owns the app-private files behind the library: one PDF per
 * imported document, one thumbnail per entry.
 *
 * Imports are copied in here, so the library never depends on the
 * source file surviving.
 *
 * @param context Any context; only the application context is kept.
 */
class MapFileStore(context: Context) {

    private val root: File = context.applicationContext.filesDir

    /** Directory holding the imported PDFs. */
    val pdfDir: File = File(root, "maps").apply { mkdirs() }

    /** Directory holding one thumbnail PNG per library entry. */
    val thumbDir: File = File(root, "thumbs").apply { mkdirs() }

    /**
     * Locates the stored PDF for a document.
     *
     * @param documentId Id of the source PDF.
     * @return The file, which may not exist yet.
     */
    fun pdfFile(documentId: String): File =
        File(pdfDir, "$documentId.pdf")

    /**
     * Locates the thumbnail for a library entry.
     *
     * @param entryId Id of the entry.
     * @return The file, which may not exist yet.
     */
    fun thumbFile(entryId: String): File =
        File(thumbDir, "$entryId.png")

    /**
     * Deletes a stored PDF, ignoring a missing file.
     *
     * @param documentId Id of the source PDF.
     */
    fun deletePdf(documentId: String) {
        pdfFile(documentId).delete()
    }

    /**
     * Deletes a thumbnail, ignoring a missing file.
     *
     * @param entryId Id of the entry.
     */
    fun deleteThumb(entryId: String) {
        thumbFile(entryId).delete()
    }
}
