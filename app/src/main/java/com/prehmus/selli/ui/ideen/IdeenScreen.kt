package com.prehmus.selli.ui.ideen

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import com.prehmus.selli.domain.model.NoteFolder

/**
 * Sitzt in `SelliShell` zwischen `SelliTopBar`/`SelliBottomBar` — `contentWindowInsets`
 * bleibt deshalb auf 0 (siehe Begründung im Kalender-Bugfix vom 14.09.2026: dieses
 * Scaffold berührt nie die echten Bildschirmkanten, ein Reservieren von Systemleisten-
 * Insets hier würde nur einen ungenutzten schwarzen Balken erzeugen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeenScreen(
    viewModel: IdeenViewModel,
    onOpenFolder: (folderId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            onClick = { onOpenFolder(folder.id) },
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

@Composable
private fun FolderCard(
    folder: NoteFolder,
    openCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = folder.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "$openCount offen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Optionen")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
    }
}

/** Wiederverwendet von `FolderDetailScreen` (Task 16) für den "Noch keine Punkte"-Leerzustand. */
@Composable
internal fun IdeenEmptyHint(message: String, modifier: Modifier = Modifier) {
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
