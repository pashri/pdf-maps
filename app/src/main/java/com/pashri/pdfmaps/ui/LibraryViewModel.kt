package com.pashri.pdfmaps.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pashri.pdfmaps.PdfMapsApp
import com.pashri.pdfmaps.data.ImportResult
import com.pashri.pdfmaps.data.MapEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the library screen.
 *
 * @property starred Starred entries, alphabetical.
 * @property others Everything else, alphabetical.
 * @property isEmpty True when nothing has been imported yet.
 */
data class LibraryUiState(
    val starred: List<MapEntry> = emptyList(),
    val others: List<MapEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = starred.isEmpty() && others.isEmpty()
}

/**
 * Backs the library screen: the grouped list, imports, and the
 * rename/star/delete actions.
 *
 * @param application The running application.
 */
class LibraryViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository =
        (application as PdfMapsApp).repository

    /** The library, split into its starred and unstarred groups. */
    val uiState: StateFlow<LibraryUiState> = repository.observeAll()
        .map { entries ->
            LibraryUiState(
                starred = entries.filter { it.isStarred },
                others = entries.filterNot { it.isStarred },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    private val _importing = MutableStateFlow(false)

    /** True while an import is in progress. */
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A one-shot message to show the user, or null. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Imports a PDF in the background and reports the outcome.
     *
     * @param uri Content URI of the PDF to import.
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            when (val result = repository.import(uri)) {
                is ImportResult.Success -> _message.value =
                    if (result.pageCount == 1) "Added ${result.name}"
                    else "Added ${result.pageCount} pages"

                is ImportResult.Failure -> _message.value = result.reason
            }
            _importing.value = false
        }
    }

    /** Clears the pending message once it has been shown. */
    fun messageShown() {
        _message.value = null
    }

    /**
     * Renames an entry.
     *
     * @param entry Entry to rename.
     * @param name New display name.
     */
    fun rename(entry: MapEntry, name: String) {
        viewModelScope.launch { repository.rename(entry.id, name) }
    }

    /**
     * Toggles an entry's starred state.
     *
     * @param entry Entry to toggle.
     */
    fun toggleStar(entry: MapEntry) {
        viewModelScope.launch {
            repository.setStarred(entry.id, !entry.isStarred)
        }
    }

    /**
     * Deletes an entry and its files.
     *
     * @param entry Entry to delete.
     */
    fun delete(entry: MapEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    /**
     * Resolves the thumbnail path for an entry.
     *
     * @param entry Entry to resolve.
     * @return Absolute path to the thumbnail PNG.
     */
    fun thumbnailPath(entry: MapEntry): String =
        repository.thumbFile(entry).absolutePath
}
