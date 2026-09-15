package com.prehmus.selli.ui.ideen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prehmus.selli.R
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem

/**
 * Sitzt in `SelliShell` zwischen `SelliTopBar`/`SelliBottomBar` — `contentWindowInsets`
 * bleibt deshalb auf 0 (siehe Begründung im Kalender-Bugfix vom 14.09.2026: dieses
 * Scaffold berührt nie die echten Bildschirmkanten, ein Reservieren von Systemleisten-
 * Insets hier würde nur einen ungenutzten schwarzen Balken erzeugen).
 *
 * Ordner klappen seit dem 15.09.2026 direkt hier auf, statt auf eine eigene Unterseite zu
 * führen — der Weg zum eigentlichen Inhalt war sonst ein Klick zu lang. [initialExpandedFolderId]
 * hält die Navigation von außen am Leben (Route `ideen/{folderId}`, Benachrichtigung „Neue
 * Idee"): der genannte Ordner startet aufgeklappt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeenScreen(
    viewModel: IdeenViewModel,
    modifier: Modifier = Modifier,
    initialExpandedFolderId: String? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialExpandedFolderId) {
        initialExpandedFolderId?.let(viewModel::expandFolder)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage()
        }
    }

    // Kein Realtime-Sync (siehe Spec): Der Tab hängt an der NavHost-Backstack-Entry und
    // überlebt dank saveState/restoreState den Tab-Wechsel — ohne diesen Hook würde nach
    // dem allerersten Besuch nie wieder automatisch nachgeladen, nur noch per Pull-to-Refresh.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreateFolderSheet) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ordner anlegen")
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (uiState.folders.isEmpty() && !uiState.isRefreshing) {
                IdeenEmptyHint(
                    message = "Noch keine Ordner",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = uiState.folders, key = { it.id }) { folder ->
                        FolderCard(
                            folder = folder,
                            openCount = uiState.openCountByFolder[folder.id] ?: 0,
                            isExpanded = folder.id in uiState.expandedFolderIds,
                            openItems = uiState.openItems(folder.id),
                            archivedItems = uiState.archivedItems(folder.id),
                            isArchiveExpanded = folder.id in uiState.expandedArchiveFolderIds,
                            onToggleExpanded = { viewModel.toggleFolderExpanded(folder.id) },
                            onToggleArchive = { viewModel.toggleArchiveExpanded(folder.id) },
                            onAddItem = { viewModel.openAddItemSheet(folder.id) },
                            onItemCheckedChange = viewModel::setChecked,
                            onItemClick = viewModel::beginEditingItem,
                            onRename = { viewModel.beginRenaming(folder) },
                            onDelete = { viewModel.beginDeleting(folder) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.isCreateFolderSheetOpen) {
        NameInputSheet(
            title = "Ordner anlegen",
            initialValue = "",
            onSave = viewModel::createFolder,
            onDismiss = viewModel::dismissCreateFolderSheet,
        )
    }

    uiState.renamingFolder?.let { folder ->
        NameInputSheet(
            title = "Ordner umbenennen",
            initialValue = folder.name,
            onSave = viewModel::renameFolder,
            onDismiss = viewModel::dismissRenaming,
        )
    }

    if (uiState.addingItemToFolder != null) {
        NoteItemSheet(
            title = "Punkt hinzufügen",
            initialText = "",
            initialUrl = "",
            isSaving = uiState.isSavingItem,
            onSave = viewModel::addItem,
            onDismiss = viewModel::dismissAddItemSheet,
        )
    }

    uiState.editingItem?.let { item ->
        NoteItemSheet(
            title = "Punkt bearbeiten",
            initialText = item.text,
            initialUrl = item.url.orEmpty(),
            isSaving = false,
            onSave = viewModel::saveItemEdit,
            onDismiss = viewModel::dismissEditingItem,
            onDelete = { viewModel.beginDeletingItem(item) },
        )
    }

    uiState.deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeletingItem,
            title = { Text("Punkt löschen?") },
            text = { Text("\"${item.text}\" wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteItem) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeletingItem) { Text("Abbrechen") }
            },
        )
    }

    uiState.deletingFolder?.let { folder ->
        val total = uiState.totalCountByFolder[folder.id] ?: 0
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleting,
            title = { Text("Ordner löschen?") },
            text = {
                Text("\"${folder.name}\" und alle $total Punkte darin werden endgültig gelöscht.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleting) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Ordnerkarte mit aufklappbarem Inhalt. Kopfzeile und Punkte liegen in derselben Karte,
 * damit ein aufgeklappter Ordner als ein zusammenhängendes Stück lesbar bleibt.
 */
@Composable
private fun FolderCard(
    folder: NoteFolder,
    openCount: Int,
    isExpanded: Boolean,
    openItems: List<NoteItem>,
    archivedItems: List<NoteItem>,
    isArchiveExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddItem: () -> Unit,
    onItemCheckedChange: (NoteItem, Boolean) -> Unit,
    onItemClick: (NoteItem) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "folderChevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = folder.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "$openCount offen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Ordner zuklappen" else "Ordner aufklappen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Wert erst im Layout-/Draw-Schritt lesen statt im Composable-Rumpf.
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Optionen")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Umbenennen") },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (openItems.isEmpty() && archivedItems.isEmpty()) {
                        Text(
                            text = "Noch keine Punkte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    openItems.forEach { item ->
                        NoteItemCard(
                            item = item,
                            onCheckedChange = { checked -> onItemCheckedChange(item, checked) },
                            onClick = { onItemClick(item) },
                        )
                    }

                    if (archivedItems.isNotEmpty()) {
                        TextButton(
                            onClick = onToggleArchive,
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Text(
                                if (isArchiveExpanded) {
                                    "Erledigt (${archivedItems.size}) ausblenden"
                                } else {
                                    "Erledigt (${archivedItems.size})"
                                },
                            )
                        }
                        AnimatedVisibility(visible = isArchiveExpanded) {
                            Column {
                                archivedItems.forEach { item ->
                                    NoteItemCard(
                                        item = item,
                                        onCheckedChange = { checked ->
                                            onItemCheckedChange(item, checked)
                                        },
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    TextButton(onClick = onAddItem, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = "Punkt hinzufügen", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Leerzustand der Ordnerliste. */
@Composable
private fun IdeenEmptyHint(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_empty_state),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Text(text = message, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameInputSheet(
    title: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
        }
    }
}
