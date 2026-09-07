package com.pashri.pdfmaps.render

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Edge length of a square tile, in pixels. */
const val TILE_SIZE = 512

/** Highest zoom level rendered; level N is 2^N times fit scale. */
const val MAX_TILE_LEVEL = 4

/**
 * Turns a viewport into a set of rendered tiles.
 *
 * Tiles are addressed by zoom *level* rather than by continuous
 * scale, so panning at a steady zoom reuses cached tiles instead of
 * re-rendering the page on every frame.
 *
 * @param source Document to render from.
 * @param pageIndex Page being viewed.
 * @param fitScale Scale at which the whole page fits the viewport;
 *   level 0 renders at this scale.
 * @param cache Bitmap cache shared across the viewer's lifetime.
 */
class TileRenderer(
    private val source: PdfDocumentSource,
    private val pageIndex: Int,
    private val fitScale: Float,
    private val cache: TileCache,
) {

    private val inFlight = mutableSetOf<TileKey>()
    private val guard = Mutex()

    /**
     * Chooses the zoom level to render at for a given scale.
     *
     * Rounds up, so tiles are never rendered coarser than the
     * screen shows them; text stays sharp at the cost of some extra
     * pixels.
     *
     * @param scale Current absolute scale, page points to pixels.
     * @return A level between 0 and [MAX_TILE_LEVEL].
     */
    fun levelFor(scale: Float): Int {
        val ratio = max(scale / fitScale, 1f)
        val level = ceil(ln(ratio.toDouble()) / ln(2.0)).toInt()
        return level.coerceIn(0, MAX_TILE_LEVEL)
    }

    /**
     * The absolute scale a level's tiles are rendered at.
     *
     * @param level Zoom level.
     * @return Scale in pixels per page point.
     */
    fun scaleForLevel(level: Int): Float =
        fitScale * 2f.pow(level)

    /**
     * Lists the tiles covering a viewport.
     *
     * @param level Zoom level to address tiles in.
     * @param visible Visible region, in the level's scaled page
     *   coordinates.
     * @param pageWidth Page width in points.
     * @param pageHeight Page height in points.
     * @return Every tile overlapping [visible], in row-major order.
     */
    fun tilesFor(
        level: Int,
        visible: Rect,
        pageWidth: Int,
        pageHeight: Int,
    ): List<TileKey> {
        val levelScale = scaleForLevel(level)
        val maxCol = lastIndex(pageWidth * levelScale)
        val maxRow = lastIndex(pageHeight * levelScale)

        val firstCol = (visible.left / TILE_SIZE).coerceIn(0, maxCol)
        val lastCol = (visible.right / TILE_SIZE).coerceIn(0, maxCol)
        val firstRow = (visible.top / TILE_SIZE).coerceIn(0, maxRow)
        val lastRow = (visible.bottom / TILE_SIZE).coerceIn(0, maxRow)

        return buildList {
            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) {
                    add(TileKey(pageIndex, level, col, row))
                }
            }
        }
    }

    /**
     * Index of the last tile along an axis of the given length.
     *
     * @param extent Axis length in scaled pixels.
     * @return Zero-based index of the final tile.
     */
    private fun lastIndex(extent: Float): Int =
        ((ceil(extent / TILE_SIZE).toInt()) - 1).coerceAtLeast(0)

    /**
     * The scaled-page rectangle a tile covers, clipped to the page.
     *
     * @param key Tile to measure.
     * @param pageWidth Page width in points.
     * @param pageHeight Page height in points.
     * @return The tile's rectangle in scaled page coordinates.
     */
    fun boundsOf(key: TileKey, pageWidth: Int, pageHeight: Int): Rect {
        val levelScale = scaleForLevel(key.level)
        val right = (pageWidth * levelScale).toInt()
        val bottom = (pageHeight * levelScale).toInt()
        return Rect(
            key.col * TILE_SIZE,
            key.row * TILE_SIZE,
            ((key.col + 1) * TILE_SIZE).coerceAtMost(right),
            ((key.row + 1) * TILE_SIZE).coerceAtMost(bottom),
        )
    }

    /**
     * Returns a tile if it is already rendered.
     *
     * @param key Tile to look up.
     * @return The cached bitmap, or null.
     */
    fun cached(key: TileKey): Bitmap? = cache[key]

    /**
     * Renders a tile unless it is cached or already being rendered.
     *
     * @param key Tile to render.
     * @param pageWidth Page width in points.
     * @param pageHeight Page height in points.
     * @return True if a new tile was added to the cache.
     * @throws java.io.IOException If the page cannot be rendered.
     */
    suspend fun render(
        key: TileKey,
        pageWidth: Int,
        pageHeight: Int,
    ): Boolean {
        val claimed = guard.withLock {
            if (cache[key] != null || key in inFlight) false
            else inFlight.add(key)
        }
        if (!claimed) return false

        try {
            val bounds = boundsOf(key, pageWidth, pageHeight)
            if (bounds.isEmpty) return false
            val bitmap = source.renderRegion(
                pageIndex = key.pageIndex,
                region = bounds,
                scale = scaleForLevel(key.level),
            )
            guard.withLock { cache[key] = bitmap }
            return true
        } finally {
            guard.withLock { inFlight.remove(key) }
        }
    }
}
