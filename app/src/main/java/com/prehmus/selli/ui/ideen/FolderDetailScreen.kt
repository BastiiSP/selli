package com.prehmus.selli.ui.ideen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import com.prehmus.selli.domain.model.NoteItem

/**
 * Sitzt in `SelliShell` zwischen `SelliTopBar`/`SelliBottomBar` — `contentWindowInsets`
 * bleibt deshalb auf 0 (siehe Begründung im Kalender-Bugfix vom 14.09.2026: dieses
 * Scaffold berührt nie die echten Bildschirmkanten, ein Reservieren von Systemleisten-
 * Insets hier würde nur einen ungenutzten schwarzen Balken erzeugen). Der eigene Header
 * hier (statt `SelliTopBar`) ersetzt die Route nicht das Gerüst, sondern liegt als
 * Detail-Route darunter und braucht einen eigenen Zurück-Pfeil samt Ordnernamen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmationFor by remember { mutableStateOf<NoteItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage()
        }
    }

    // Kein Realtime-Sync (siehe Spec): Das ViewModel hängt an der NavHost-Backstack-Entry und
    // überlebt damit ein Verlassen/Wiederbetreten dieser Route — ohne diesen Hook würde nach
    // dem allerersten Besuch nie wieder automatisch nachgeladen (analog ExpensesScreen).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück")
                }
                Text(text = uiState.folderName, style = MaterialTheme.typography.titleLarge)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddItemSheet) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Punkt hinzufügen")
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
            if (uiState.openItems.isEmpty() && uiState.archivedItems.isEmpty() && !uiState.isRefreshing) {
                IdeenEmptyHint(
                    message = "Noch keine Punkte",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = uiState.openItems, key = { it.id }) { item ->
                        NoteItemCard(
                            item = item,
                            onCheckedChange = { checked -> viewModel.setChecked(item, checked) },
                            onClick = { viewModel.beginEditing(item) },
                        )
                    }
                    if (uiState.archivedItems.isNotEmpty()) {
                        item(key = "archive-toggle") {
                            TextButton(onClick = viewModel::toggleArchiveExpanded) {
                                Text(
                                    if (uiState.isArchiveExpanded) {
                                        "Erledigt (${uiState.archivedItems.size}) ausblenden"
                                    } else {
                                        "Erledigt (${uiState.archivedItems.size})"
                                    },
                                )
                            }
                        }
                        if (uiState.isArchiveExpanded) {
                            items(items = uiState.archivedItems, key = { "archived-${it.id}" }) { item ->
                                NoteItemCard(
                                    item = item,
                                    onCheckedChange = { checked -> viewModel.setChecked(item, checked) },
                                    onClick = { viewModel.beginEditing(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isAddItemSheetOpen) {
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
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::dismissEditing,
            onDelete = { showDeleteConfirmationFor = item },
        )
    }

    showDeleteConfirmationFor?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationFor = null },
            title = { Text("Punkt löschen?") },
            text = { Text("\"${item.text}\" wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item.id)
                    showDeleteConfirmationFor = null
                    viewModel.dismissEditing()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationFor = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun NoteItemCard(
    item: NoteItem,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick),
                )
            }
            item.url?.takeUnless(String::isBlank)?.let { url ->
                LinkPreviewCard(
                    url = url,
                    title = item.previewTitle,
                    imageUrl = item.previewImageUrl,
                    modifier = Modifier
                        .padding(start = 48.dp, end = 12.dp, bottom = 8.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            runCatching { context.startActivity(intent) }
                        },
                )
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(
    url: String,
    title: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
            Text(
                text = title ?: shortenUrlForDisplay(url),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun shortenUrlForDisplay(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteItemSheet(
    title: String,
    initialText: String,
    initialUrl: String,
    isSaving: Boolean,
    onSave: (text: String, url: String?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    var url by remember { mutableStateOf(initialUrl) }

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
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(text, url.takeUnless(String::isBlank)) },
                enabled = text.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isSaving) "Wird gespeichert …" else "Speichern") }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
