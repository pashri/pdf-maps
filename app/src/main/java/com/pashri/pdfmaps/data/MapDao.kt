package com.pashri.pdfmaps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room access to the map library.
 *
 * Ordering is done in SQL rather than in the UI so that every
 * observer sees the same order.
 */
@Dao
interface MapDao {

    /**
     * Observes the whole library in display order: starred entries
     * first, alphabetical within each group.
     *
     * `COLLATE NOCASE` keeps "arnside" beside "Arnside" instead of
     * sorting every lower-case name after every capitalised one.
     *
     * @return A flow that re-emits whenever the library changes.
     */
    @Query(
        """
        SELECT * FROM map_entries
        ORDER BY isStarred DESC, displayName COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<MapEntry>>

    /**
     * Observes a single entry.
     *
     * @param id Id of the entry to observe.
     * @return A flow emitting the entry, or null once it is deleted.
     */
    @Query("SELECT * FROM map_entries WHERE id = :id")
    fun observeById(id: String): Flow<MapEntry?>

    /**
     * Reads a single entry once.
     *
     * @param id Id of the entry to read.
     * @return The entry, or null if no such row exists.
     */
    @Query("SELECT * FROM map_entries WHERE id = :id")
    suspend fun findById(id: String): MapEntry?

    /**
     * Inserts the pages of one import.
     *
     * @param entries Rows to insert; normally all pages of one PDF.
     */
    @Insert
    suspend fun insertAll(entries: List<MapEntry>)

    /**
     * Renames one entry.
     *
     * @param id Id of the entry to rename.
     * @param name New display name.
     */
    @Query("UPDATE map_entries SET displayName = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    /**
     * Stars or unstars one entry.
     *
     * @param id Id of the entry to update.
     * @param starred True to pin it to the Starred section.
     */
    @Query("UPDATE map_entries SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    /**
     * Persists the viewport so the map reopens where it was left.
     *
     * @param id Id of the entry being viewed.
     * @param scale Current zoom factor.
     * @param centerX Viewport centre X, in page coordinates.
     * @param centerY Viewport centre Y, in page coordinates.
     */
    @Query(
        """
        UPDATE map_entries
        SET scale = :scale, centerX = :centerX, centerY = :centerY
        WHERE id = :id
        """
    )
    suspend fun saveViewport(
        id: String,
        scale: Float,
        centerX: Float,
        centerY: Float,
    )

    /**
     * Deletes one entry. Does not touch files on disk.
     *
     * @param id Id of the entry to delete.
     */
    @Query("DELETE FROM map_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Counts the rows still referencing a stored PDF, to decide
     * whether the file itself can be deleted.
     *
     * @param documentId Id of the source PDF.
     * @return Number of remaining entries for that document.
     */
    @Query("SELECT COUNT(*) FROM map_entries WHERE documentId = :documentId")
    suspend fun countForDocument(documentId: String): Int
}
