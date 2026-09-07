package com.pashri.pdfmaps

import android.app.Application
import com.pashri.pdfmaps.data.MapDatabase
import com.pashri.pdfmaps.data.MapFileStore
import com.pashri.pdfmaps.data.MapRepository

/**
 * Application entry point, and the app's service locator.
 *
 * The app is small enough that a lazily built repository beats a
 * dependency-injection framework.
 */
class PdfMapsApp : Application() {

    /** The shared map library repository. */
    val repository: MapRepository by lazy {
        MapRepository(
            context = this,
            dao = MapDatabase.get(this).mapDao(),
            files = MapFileStore(this),
        )
    }
}
