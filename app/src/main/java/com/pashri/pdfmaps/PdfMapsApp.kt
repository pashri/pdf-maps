package com.pashri.pdfmaps

import android.app.Application
import com.pashri.pdfmaps.data.MapDatabase
import com.pashri.pdfmaps.data.MapFileStore
import com.pashri.pdfmaps.data.MapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application entry point, and the app's service locator.
 *
 * The app is small enough that a lazily built repository beats a
 * dependency-injection framework.
 */
class PdfMapsApp : Application() {

    /**
     * Scope for writes that must outlive the screen asking for
     * them. Saving the viewer's position happens as the viewer is
     * being torn down, by which point a `viewModelScope` has
     * already been cancelled and the write would be dropped.
     */
    val persistenceScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The shared map library repository. */
    val repository: MapRepository by lazy {
        MapRepository(
            context = this,
            dao = MapDatabase.get(this).mapDao(),
            files = MapFileStore(this),
        )
    }
}
