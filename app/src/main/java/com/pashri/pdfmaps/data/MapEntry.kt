package com.pashri.pdfmaps.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One imported map: a single page of a single imported PDF.
 *
 * A multi-page import produces one row per page, all sharing a
 * [documentId] and therefore one PDF file on disk. The file is
 * deleted only when the last row referencing it is deleted.
 *
 * @property id Stable unique id; also the thumbnail filename stem.
 * @property documentId Id of the source PDF; the filename stem of
 *   the stored file. Shared by every page of one import.
 * @property pageIndex Zero-based page within the source PDF.
 * @property pageCount Total pages in the source PDF.
 * @property displayName Name shown in the library; user-editable.
 * @property originalFileName Filename as imported, kept for
 *   reference after [displayName] has been edited.
 * @property isStarred Whether the map is pinned to the Starred
 *   section at the top of the library.
 * @property importedAt Import time in epoch milliseconds.
 * @property scale Saved zoom factor, or null if never opened.
 * @property centerX Saved viewport centre X, in page coordinates.
 * @property centerY Saved viewport centre Y, in page coordinates.
 */
@Entity(tableName = "map_entries", indices = [Index("documentId")])
data class MapEntry(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val pageCount: Int,
    val displayName: String,
    val originalFileName: String,
    val isStarred: Boolean = false,
    val importedAt: Long = System.currentTimeMillis(),
    val scale: Float? = null,
    val centerX: Float? = null,
    val centerY: Float? = null,
)
