package com.pashri.pdfmaps.render

/**
 * Identifies one rendered tile.
 *
 * @property pageIndex Page the tile belongs to.
 * @property level Zoom level; the tile is rendered at
 *   `2^level` times the fit-to-screen scale.
 * @property col Tile column within the level's grid.
 * @property row Tile row within the level's grid.
 */
data class TileKey(
    val pageIndex: Int,
    val level: Int,
    val col: Int,
    val row: Int,
)
