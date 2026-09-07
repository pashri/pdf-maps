package com.pashri.pdfmaps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pashri.pdfmaps.ui.LibraryScreen
import com.pashri.pdfmaps.ui.LibraryViewModel
import com.pashri.pdfmaps.ui.MapViewerScreen
import com.pashri.pdfmaps.ui.MapViewerViewModel
import com.pashri.pdfmaps.ui.PdfMapsTheme

/** Route for the library list. */
private const val ROUTE_LIBRARY = "library"

/** Route for the viewer, parameterised by entry id. */
private const val ROUTE_VIEWER = "viewer/{entryId}"

/** Navigation argument holding the entry id. */
private const val ARG_ENTRY_ID = "entryId"

/**
 * The app's only activity: hosts the library and viewer, and takes
 * PDFs shared or opened from other apps.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PdfMapsTheme {
                PdfMapsNavHost(incoming = pdfUriFrom(intent))
            }
        }
    }

    /**
     * Handles a PDF shared while the app was already running.
     *
     * @param intent The new intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /**
     * Extracts a PDF URI from a share or view intent.
     *
     * @param intent Intent that started or resumed the activity.
     * @return The URI to import, or null if there is nothing to do.
     */
    private fun pdfUriFrom(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND ->
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)

        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}

/**
 * The navigation graph: library, then viewer.
 *
 * @param incoming A PDF URI to import on launch, if the app was
 *   opened by a share or view intent.
 */
@Composable
private fun PdfMapsNavHost(incoming: Uri?) {
    val controller = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel()
    var consumed by remember { mutableStateOf(false) }

    LaunchedEffect(incoming) {
        if (incoming != null && !consumed) {
            consumed = true
            libraryViewModel.import(incoming)
        }
    }

    NavHost(controller, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) {
            LibraryScreen(
                onOpenMap = { id -> controller.navigate("viewer/$id") },
                viewModel = libraryViewModel,
            )
        }
        composable(
            route = ROUTE_VIEWER,
            arguments = listOf(
                navArgument(ARG_ENTRY_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val id = entry.arguments?.getString(ARG_ENTRY_ID).orEmpty()
            MapViewerScreen(
                onBack = { controller.popBackStack() },
                viewModel = viewModel(
                    factory = MapViewerViewModelFactory(
                        application = LocalContext.current
                            .applicationContext as PdfMapsApp,
                        entryId = id,
                    ),
                ),
            )
        }
    }
}

/**
 * Builds a [MapViewerViewModel] for a specific entry.
 *
 * @param application The running application.
 * @param entryId Id of the map to open.
 */
class MapViewerViewModelFactory(
    private val application: PdfMapsApp,
    private val entryId: String,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MapViewerViewModel(application, entryId) as T
}
