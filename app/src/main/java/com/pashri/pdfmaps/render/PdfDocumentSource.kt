package com.pashri.pdfmaps.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Size
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serialised access to one PDF file.
 *
 * [PdfRenderer] is not thread-safe and allows only one open page at
 * a time. Every call here is guarded by a single [Mutex] and run on
 * a background dispatcher, which is what stops fast panning from
 * crashing the app.
 *
 * Callers must [close] the source when finished with it.
 *
 * @param file The stored PDF to read.
 * @param dispatcher Dispatcher used for all rendering work.
 */
class PdfDocumentSource(
    private val file: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

    private val mutex = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    /**
     * Opens the document if it is not already open.
     *
     * @return The live renderer.
     * @throws java.io.IOException If the file is missing, corrupt or
     *   password-protected.
     */
    private fun openLocked(): PdfRenderer {
        renderer?.let { return it }
        val fd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        return PdfRenderer(fd).also {
            descriptor = fd
            renderer = it
        }
    }

    /**
     * Counts the pages in the document.
     *
     * @return The page count.
     * @throws java.io.IOException If the document cannot be opened.
     */
    suspend fun pageCount(): Int = withContext(dispatcher) {
        mutex.withLock { openLocked().pageCount }
    }

    /**
     * Reads the natural size of a page, in PDF points.
     *
     * @param pageIndex Zero-based page index.
     * @return The page size in points.
     * @throws java.io.IOException If the document cannot be opened.
     */
    suspend fun pageSize(pageIndex: Int): Size = withContext(dispatcher) {
        mutex.withLock {
            openLocked().openPage(pageIndex).use { page ->
                Size(page.width, page.height)
            }
        }
    }

    /**
     * Renders a rectangular region of a page at a given zoom.
     *
     * The region is expressed in *scaled* page coordinates, i.e. in
     * the coordinate space of the page multiplied by [scale], which
     * is how the tile grid addresses it.
     *
     * @param pageIndex Zero-based page index.
     * @param region Region to render, in scaled page coordinates.
     * @param scale Zoom factor applied to the page.
     * @return A bitmap the size of [region], white where the page
     *   is blank.
     * @throws java.io.IOException If the document cannot be opened.
     */
    suspend fun renderRegion(
        pageIndex: Int,
        region: Rect,
        scale: Float,
    ): Bitmap = withContext(dispatcher) {
        val bitmap = Bitmap.createBitmap(
            region.width().coerceAtLeast(1),
            region.height().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        // PdfRenderer composites onto whatever is already there, so
        // an unpainted PDF background would come out transparent.
        bitmap.eraseColor(Color.WHITE)

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-region.left.toFloat(), -region.top.toFloat())
        }

        mutex.withLock {
            openLocked().openPage(pageIndex).use { page ->
                page.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )
            }
        }
        bitmap
    }

    /**
     * Closes the renderer and its file descriptor. Safe to call
     * more than once.
     */
    override fun close() {
        renderer?.close()
        renderer = null
        descriptor?.close()
        descriptor = null
    }
}
