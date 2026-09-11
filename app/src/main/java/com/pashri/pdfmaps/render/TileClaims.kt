package com.pashri.pdfmaps.render

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tracks which tiles are being rendered, so the same tile is never
 * rendered twice at once.
 *
 * Renders are routinely cancelled mid-flight, because the viewer's
 * render loop restarts whenever panning changes the visible tile
 * set, so [release] normally runs in an already-cancelled
 * coroutine. It takes [NonCancellable] to make that safe by
 * construction: a stranded claim would make its tile unrenderable
 * for the rest of the session. Today an uncontended [Mutex] would
 * acquire without suspending anyway, so this guards the guarantee
 * rather than a reachable failure.
 */
class TileClaims {

    private val held = mutableSetOf<TileKey>()
    private val guard = Mutex()

    /**
     * Claims a tile for rendering.
     *
     * @param key Tile to claim.
     * @return True if the caller took the claim and must [release]
     *   it; false if another render already holds it.
     */
    suspend fun claim(key: TileKey): Boolean = guard.withLock {
        held.add(key)
    }

    /**
     * Gives up a claim taken by [claim].
     *
     * @param key Tile to release.
     */
    suspend fun release(key: TileKey) {
        withContext(NonCancellable) {
            guard.withLock { held.remove(key) }
        }
    }

    /**
     * Whether a tile is currently claimed.
     *
     * @param key Tile to check.
     * @return True if some render holds a claim on it.
     */
    suspend fun isHeld(key: TileKey): Boolean = guard.withLock {
        key in held
    }
}
