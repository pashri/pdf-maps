package com.pashri.pdfmaps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The app's only database, holding the map library. */
@Database(entities = [MapEntry::class], version = 1, exportSchema = true)
abstract class MapDatabase : RoomDatabase() {

    /**
     * Access to the map library.
     *
     * @return The library DAO.
     */
    abstract fun mapDao(): MapDao

    companion object {
        @Volatile
        private var instance: MapDatabase? = null

        /**
         * Returns the process-wide database singleton, creating it
         * on first call. Safe to call from any thread.
         *
         * @param context Any context; the application context is
         *   retained rather than the one passed in.
         * @return The shared database instance.
         */
        fun get(context: Context): MapDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MapDatabase::class.java,
                    "pdf-maps.db",
                ).build().also { instance = it }
            }
    }
}
