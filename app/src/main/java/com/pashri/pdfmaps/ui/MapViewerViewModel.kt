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

    private val repository = (application as PdfMapsApp).repository

    private val _uiState =
        MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)

    /** The viewer's current state. */
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

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
     * Saves the viewport so the map reopens where it was left.
     *
     * @param scale Current zoom factor.
     * @param centerX Viewport centre X, in page coordinates.
     * @param centerY Viewport centre Y, in page coordinates.
     */
    fun saveViewport(scale: Float, centerX: Float, centerY: Float) {
        viewModelScope.launch {
            repository.saveViewport(entryId, scale, centerX, centerY)
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
