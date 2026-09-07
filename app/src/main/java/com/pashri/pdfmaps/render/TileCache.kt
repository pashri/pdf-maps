package com.pashri.pdfmaps.render

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

/** Fraction of the app's heap budget the tile cache may use. */
private const val HEAP_FRACTION = 0.25f

/** Bytes per megabyte, for turning `memoryClass` into a size. */
private const val BYTES_PER_MB = 1024 * 1024

/**
 * A memory-bounded cache of rendered tiles.
 *
 * Sized against the device's heap budget rather than a fixed
 * number of tiles, so a cheap phone caches fewer of them instead of
 * dying.
 *
 * @param maxBytes Maximum total bitmap bytes to retain.
 */
class TileCache(maxBytes: Int) {

    private val cache = object : LruCache<TileKey, Bitmap>(maxBytes) {
        override fun sizeOf(key: TileKey, value: Bitmap): Int =
            value.byteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: TileKey,
            oldValue: Bitmap,
            newValue: Bitmap?,
        ) {
            // Tiles are never handed out after eviction, so this is
            // safe and keeps native memory from lingering.
            if (evicted || oldValue !== newValue) oldValue.recycle()
        }
    }

    /**
     * Looks up a tile.
     *
     * @param key Tile to look up.
     * @return The bitmap, or null if it is not cached.
     */
    operator fun get(key: TileKey): Bitmap? = cache[key]

    /**
     * Stores a tile.
     *
     * @param key Tile being stored.
     * @param bitmap Rendered bitmap.
     */
    operator fun set(key: TileKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Drops every cached tile and recycles the bitmaps. */
    fun clear() {
        cache.evictAll()
    }

    companion object {

        /**
         * Builds a cache sized to a fraction of this device's heap.
         *
         * @param context Any context.
         * @return A cache sized for the device.
         */
        fun forDevice(context: Context): TileCache {
            val manager = context.getSystemService(
                Context.ACTIVITY_SERVICE,
            ) as ActivityManager
            val budget =
                manager.memoryClass * BYTES_PER_MB * HEAP_FRACTION
            return TileCache(budget.toInt())
        }
    }
}
