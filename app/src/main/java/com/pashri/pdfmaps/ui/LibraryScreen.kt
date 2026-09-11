package com.pashri.pdfmaps.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pashri.pdfmaps.R
import com.pashri.pdfmaps.data.MapEntry
import java.io.File

/**
 * The library: every imported map, starred entries pinned on top
 * and alphabetical within each group.
 *
 * @param onOpenMap Called with an entry id when a map is tapped.
 * @param viewModel Backing view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenMap: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    var sheetFor by remember { mutableStateOf<MapEntry?>(null) }
    var renaming by remember { mutableStateOf<MapEntry?>(null) }
    var deleting by remember { mutableStateOf<MapEntry?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_map),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isEmpty && !importing -> EmptyLibrary()
                else -> MapList(
                    state = state,
                    thumbnailPath = viewModel::thumbnailPath,
                    onOpen = { onOpenMap(it.id) },
                    onShowActions = { sheetFor = it },
                )
            }

            if (importing) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                )
            }
        }
    }

    sheetFor?.let { entry ->
        MapActionsSheet(
            entry = entry,
            onRename = { renaming = entry; sheetFor = null },
            onToggleStar = {
                viewModel.toggleStar(entry)
                sheetFor = null
            },
            onDelete = { deleting = entry; sheetFor = null },
            onDismiss = { sheetFor = null },
        )
    }

    renaming?.let { entry ->
        RenameDialog(
            entry = entry,
            onConfirm = { name ->
                viewModel.rename(entry, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { entry ->
        ConfirmDeleteDialog(
            onConfirm = {
                viewModel.delete(entry)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * The grouped, scrolling list of maps.
 *
 * @param state Current library contents.
 * @param thumbnailPath Resolves an entry's thumbnail file path.
 * @param onOpen Called when a row is tapped.
 * @param onShowActions Called to open a row's action sheet.
 */
@Composable
private fun MapList(
    state: LibraryUiState,
    thumbnailPath: (MapEntry) -> String,
    onOpen: (MapEntry) -> Unit,
    onShowActions: (MapEntry) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.starred.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.favourites_header)) }
            items(state.starred, key = { it.id }) { entry ->
                MapRow(entry, thumbnailPath(entry), onOpen, onShowActions)
            }
            if (state.others.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.all_maps_header))
                }
            }
        }
        items(state.others, key = { it.id }) { entry ->
            MapRow(entry, thumbnailPath(entry), onOpen, onShowActions)
        }
    }
}

/**
 * A group heading within the library list.
 *
 * @param text Heading text.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 4.dp,
        ),
    )
}

/**
 * One map in the library list.
 *
 * @param entry Entry to show.
 * @param thumbnailPath Path to the entry's thumbnail PNG.
 * @param onOpen Called when the row is tapped.
 * @param onShowActions Called to open the row's action sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapRow(
    entry: MapEntry,
    thumbnailPath: String,
    onOpen: (MapEntry) -> Unit,
    onShowActions: (MapEntry) -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.displayName) },
        leadingContent = {
            AsyncImage(
                model = File(thumbnailPath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 80.dp, height = 56.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isStarred) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription =
                            stringResource(R.string.favourite),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                IconButton(onClick = { onShowActions(entry) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription =
                            stringResource(R.string.more_actions),
                    )
                }
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = { onOpen(entry) },
            onLongClick = { onShowActions(entry) },
        ),
    )
}

/** Shown when nothing has been imported yet. */
@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Actions for one map, reached by long-press or the row's overflow
 * button.
 *
 * @param entry Entry the actions apply to.
 * @param onRename Called to start renaming.
 * @param onToggleStar Called to star or unstar.
 * @param onDelete Called to start deleting.
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapActionsSheet(
    entry: MapEntry,
    onRename: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SheetAction(
            icon = Icons.Default.Edit,
            label = stringResource(R.string.rename),
            onClick = onRename,
        )
        SheetAction(
            icon =
                if (entry.isStarred) Icons.Default.StarBorder
                else Icons.Default.Star,
            label = stringResource(
                if (entry.isStarred) R.string.unfavourite
                else R.string.favourite,
            ),
            onClick = onToggleStar,
        )
        SheetAction(
            icon = Icons.Default.Delete,
            label = stringResource(R.string.delete),
            onClick = onDelete,
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * One action in the sheet.
 *
 * @param icon Leading icon.
 * @param label Action text.
 * @param onClick Called when the row is tapped.
 */
@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Prompts for a new display name.
 *
 * @param entry Entry being renamed.
 * @param onConfirm Called with the new name.
 * @param onDismiss Called when cancelled.
 */
@Composable
private fun RenameDialog(
    entry: MapEntry,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(entry.id) { mutableStateOf(entry.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Confirms deleting a map.
 *
 * @param onConfirm Called when deletion is confirmed.
 * @param onDismiss Called when cancelled.
 */
@Composable
private fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
