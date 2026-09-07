package com.pashri.pdfmaps.render

import android.graphics.Bitmap
import android.graphics.Rect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge of a generated thumbnail, in pixels. */
private const val THUMBNAIL_MAX_EDGE = 256

/** Renders small preview images of PDF pages for the library list. */
object ThumbnailGenerator {

    /**
     * Renders one page to a PNG file, scaled so its longest edge is
     * at most [THUMBNAIL_MAX_EDGE] pixels.
     *
     * @param source Open document to read from.
     * @param pageIndex Zero-based page index.
     * @param target File to write the PNG to; overwritten if
     *   present.
     * @throws java.io.IOException If rendering or writing fails.
     */
    suspend fun writeThumbnail(
        source: PdfDocumentSource,
        pageIndex: Int,
        target: File,
    ) {
        val size = source.pageSize(pageIndex)
        val longest = maxOf(size.width, size.height).coerceAtLeast(1)
        val scale = THUMBNAIL_MAX_EDGE.toFloat() / longest
        val region = Rect(
            0,
            0,
            (size.width * scale).toInt().coerceAtLeast(1),
            (size.height * scale).toInt().coerceAtLeast(1),
        )

        val bitmap = source.renderRegion(pageIndex, region, scale)
        withContext(Dispatchers.IO) {
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        bitmap.recycle()
    }
}
