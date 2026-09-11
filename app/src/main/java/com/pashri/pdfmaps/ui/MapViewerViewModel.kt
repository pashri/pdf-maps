package com.pashri.pdfmaps.ui

import android.app.Application
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pashri.pdfmaps.PdfMapsApp
import com.pashri.pdfmaps.data.MapEntry
import com.pashri.pdfmaps.render.PdfDocumentSource
import com.pashri.pdfmaps.render.TileCache
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Where the user is within a page.
 *
 * @property scale Absolute zoom, in pixels per PDF point.
 * @property centerX Viewport centre X, in page coordinates.
 * @property centerY Viewport centre Y, in page coordinates.
 */
data class Viewport(
    val scale: Float,
    val centerX: Float,
    val centerY: Float,
)

/** What the viewer screen is currently showing. */
sealed interface ViewerUiState {

    /** The page is still being opened. */
    data object Loading : ViewerUiState

    /**
     * The page is open and ready to draw.
     *
     * @property entry The map being viewed.
     * @property source Open document, serialised internally.
     * @property pageSize Page size in PDF points.
     * @property cache Tile cache for this viewing session.
     */
    data class Ready(
        val entry: MapEntry,
        val source: PdfDocumentSource,
        val pageSize: Size,
        val cache: TileCache,
    ) : ViewerUiState

    /**
     * The page could not be opened.
     *
     * @property reason Message suitable for showing to the user.
     */
    data class Failed(val reason: String) : ViewerUiState
}

/**
 * Backs the viewer screen: opens the PDF, owns the tile cache, and
 * persists the viewport.
 *
 * @param application The running application.
 * @param entryId Id of the map to open.
 */
class MapViewerViewModel(
    application: Application,
    private val entryId: String,
) : AndroidViewModel(application) {

    private val app = application as PdfMapsApp
    private val repository = app.repository

    private val _uiState =
        MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)

    /** The viewer's current state. */
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    /**
     * Where the user currently is, or null before the page has been
     * laid out.
     *
     * Deliberately a plain property rather than a [StateFlow]: it
     * changes on every frame of a pan, and nothing should recompose
     * in response. It lives here so that a configuration change
     * re-derives the on-screen offset from the live position rather
     * than from the row read when the map was opened.
     */
    var viewport: Viewport? = null
        private set

    init {
        open()
    }

    /** Opens the entry's PDF and measures its page. */
    private fun open() {
        viewModelScope.launch {
            val entry = repository.observe(entryId).first()
            if (entry == null) {
                _uiState.value = ViewerUiState.Failed("Map not found.")
                return@launch
            }
            _uiState.value = try {
                val source = PdfDocumentSource(repository.pdfFile(entry))
                ViewerUiState.Ready(
                    entry = entry,
                    source = source,
                    pageSize = source.pageSize(entry.pageIndex),
                    cache = TileCache.forDevice(getApplication()),
                )
            } catch (error: IOException) {
                ViewerUiState.Failed("Could not open this map.")
            }
        }
    }

    /**
     * Records where the viewer is, without writing it to disk.
     *
     * @param viewport Position to remember.
     */
    fun onViewportChanged(viewport: Viewport) {
        this.viewport = viewport
    }

    /**
     * Writes the remembered position so the map reopens in place.
     *
     * Runs on the application scope rather than [viewModelScope]:
     * the usual moment to save is as the viewer is torn down, and
     * [viewModelScope] is cancelled before such a write could run.
     */
    fun saveViewport() {
        val current = viewport ?: return
        app.persistenceScope.launch {
            repository.saveViewport(
                id = entryId,
                scale = current.scale,
                centerX = current.centerX,
                centerY = current.centerY,
            )
        }
    }

    /** Releases the PDF and the tile bitmaps. */
    override fun onCleared() {
        (_uiState.value as? ViewerUiState.Ready)?.let {
            it.source.close()
            it.cache.clear()
        }
        super.onCleared()
    }
}
