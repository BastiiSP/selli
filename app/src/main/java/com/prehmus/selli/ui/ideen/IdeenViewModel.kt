package com.prehmus.selli.ui.ideen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.notes.archivedItems
import com.prehmus.selli.domain.notes.openItems
import com.prehmus.selli.domain.repository.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeenUiState(
    val folders: List<NoteFolder> = emptyList(),
    /** Punkte je Ordner, offene zuerst — Quelle für Zähler *und* aufgeklappten Inhalt. */
    val itemsByFolder: Map<String, List<NoteItem>> = emptyMap(),
    val openCountByFolder: Map<String, Int> = emptyMap(),
    val totalCountByFolder: Map<String, Int> = emptyMap(),
    /** Welche Ordner gerade aufgeklappt sind. Mehrere gleichzeitig sind erlaubt. */
    val expandedFolderIds: Set<String> = emptySet(),
    /** Ordner, in denen zusätzlich der „Erledigt"-Bereich ausgeklappt ist. */
    val expandedArchiveFolderIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val isCreateFolderSheetOpen: Boolean = false,
    val renamingFolder: NoteFolder? = null,
    val deletingFolder: NoteFolder? = null,
    /** Ordner, für den gerade das „Punkt hinzufügen"-Sheet offen ist. */
    val addingItemToFolder: String? = null,
    val editingItem: NoteItem? = null,
    val deletingItem: NoteItem? = null,
    val isSavingItem: Boolean = false,
    val userMessage: String? = null,
) {
    fun openItems(folderId: String): List<NoteItem> = itemsByFolder[folderId]?.openItems().orEmpty()

    fun archivedItems(folderId: String): List<NoteItem> =
        itemsByFolder[folderId]?.archivedItems().orEmpty()
}

/**
 * Trägt seit dem Inline-Aufklappen (siehe [IdeenScreen]) auch die Punkte der Ordner — die
 * frühere Ordner-Detailseite samt eigenem ViewModel ist entfallen.
 *
 * Das kostet keinen zusätzlichen Ladevorgang: [refresh] hat die Punkte jedes Ordners schon
 * vorher geladen, um die „N offen"-Zähler zu bilden; sie werden jetzt zusätzlich behalten.
 * Aufklappen ist damit reine UI-Umschaltung ohne Nachladen.
 *
 * Kein Realtime-Sync (siehe Spec): [refresh] wird beim Öffnen des Tabs, per
 * Pull-to-Refresh und nach jeder eigenen Änderung aufgerufen.
 */
class IdeenViewModel(
    private val repository: NoteRepository,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IdeenUiState())
    val uiState: StateFlow<IdeenUiState> = _uiState.asStateFlow()

    // Ein erneuter Aufruf (Pull-to-Refresh während des Betretens) darf sich nicht mit einem
    // laufenden Refresh überlappen — sonst überschreibt eine ältere, langsamere Antwort eine
    // frischere (analog CalendarViewModel.refreshJob/ExpensesViewModel).
    private var refreshJob: Job? = null

    init { refresh() }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val folders = repository.loadFolders()
            val itemsByFolder = folders.associate { folder ->
                folder.id to repository.loadItems(folder.id)
            }
            _uiState.update { state ->
                state.copy(
                    folders = folders,
                    itemsByFolder = itemsByFolder,
                    openCountByFolder = itemsByFolder.mapValues { (_, items) ->
                        items.count { !it.isChecked }
                    },
                    totalCountByFolder = itemsByFolder.mapValues { (_, items) -> items.size },
                    // Gelöschte Ordner dürfen nicht als „aufgeklappt" zurückbleiben.
                    expandedFolderIds = state.expandedFolderIds.intersect(itemsByFolder.keys),
                    expandedArchiveFolderIds =
                        state.expandedArchiveFolderIds.intersect(itemsByFolder.keys),
                    isRefreshing = false,
                )
            }
        }
    }

    fun toggleFolderExpanded(folderId: String) {
        _uiState.update { state ->
            state.copy(
                expandedFolderIds = if (folderId in state.expandedFolderIds) {
                    state.expandedFolderIds - folderId
                } else {
                    state.expandedFolderIds + folderId
                },
            )
        }
    }

    /**
     * Klappt einen Ordner gezielt auf, ohne ihn bei erneutem Aufruf wieder zuzuklappen —
     * für die Navigation von außen (Benachrichtigung „Neue Idee", Route `ideen/{folderId}`).
     */
    fun expandFolder(folderId: String) {
        _uiState.update { it.copy(expandedFolderIds = it.expandedFolderIds + folderId) }
    }

    fun toggleArchiveExpanded(folderId: String) {
        _uiState.update { state ->
            state.copy(
                expandedArchiveFolderIds = if (folderId in state.expandedArchiveFolderIds) {
                    state.expandedArchiveFolderIds - folderId
                } else {
                    state.expandedArchiveFolderIds + folderId
                },
            )
        }
    }

    fun openCreateFolderSheet() { _uiState.update { it.copy(isCreateFolderSheetOpen = true) } }
    fun dismissCreateFolderSheet() { _uiState.update { it.copy(isCreateFolderSheetOpen = false) } }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.addFolder(name = name, createdBy = ownPerson)
                .onSuccess {
                    _uiState.update { it.copy(isCreateFolderSheetOpen = false) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Ordner konnte nicht angelegt werden.")
                    }
                }
        }
    }

    fun beginRenaming(folder: NoteFolder) { _uiState.update { it.copy(renamingFolder = folder) } }
    fun dismissRenaming() { _uiState.update { it.copy(renamingFolder = null) } }

    fun renameFolder(name: String) {
        val folder = _uiState.value.renamingFolder ?: return
        viewModelScope.launch {
            repository.renameFolder(id = folder.id, name = name)
                .onSuccess {
                    _uiState.update { it.copy(renamingFolder = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Ordner konnte nicht umbenannt werden.")
                    }
                }
        }
    }

    fun beginDeleting(folder: NoteFolder) { _uiState.update { it.copy(deletingFolder = folder) } }
    fun dismissDeleting() { _uiState.update { it.copy(deletingFolder = null) } }

    fun confirmDelete() {
        val folder = _uiState.value.deletingFolder ?: return
        viewModelScope.launch {
            repository.deleteFolder(folder.id)
                .onSuccess {
                    _uiState.update { it.copy(deletingFolder = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Ordner konnte nicht gelöscht werden.")
                    }
                }
        }
    }

    fun openAddItemSheet(folderId: String) {
        _uiState.update { it.copy(addingItemToFolder = folderId) }
    }

    fun dismissAddItemSheet() { _uiState.update { it.copy(addingItemToFolder = null) } }

    fun addItem(text: String, url: String?) {
        val folderId = _uiState.value.addingItemToFolder ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingItem = true) }
            repository.addItem(folderId = folderId, text = text, url = url, createdBy = ownPerson)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSavingItem = false, addingItemToFolder = null)
                    }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingItem = false,
                            userMessage = error.message ?: "Punkt konnte nicht gespeichert werden.",
                        )
                    }
                }
        }
    }

    fun setChecked(item: NoteItem, checked: Boolean) {
        viewModelScope.launch {
            repository.setChecked(item.id, checked)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Änderung konnte nicht gespeichert werden.")
                    }
                }
        }
    }

    fun beginEditingItem(item: NoteItem) { _uiState.update { it.copy(editingItem = item) } }
    fun dismissEditingItem() { _uiState.update { it.copy(editingItem = null) } }

    fun saveItemEdit(text: String, url: String?) {
        val item = _uiState.value.editingItem ?: return
        viewModelScope.launch {
            repository.updateItem(id = item.id, text = text, url = url)
                .onSuccess {
                    _uiState.update { it.copy(editingItem = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Änderung konnte nicht gespeichert werden.")
                    }
                }
        }
    }

    fun beginDeletingItem(item: NoteItem) { _uiState.update { it.copy(deletingItem = item) } }
    fun dismissDeletingItem() { _uiState.update { it.copy(deletingItem = null) } }

    fun confirmDeleteItem() {
        val item = _uiState.value.deletingItem ?: return
        viewModelScope.launch {
            repository.deleteItem(item.id)
                .onSuccess {
                    // Das Bearbeiten-Sheet hängt am selben Punkt — mit wegräumen.
                    _uiState.update { it.copy(deletingItem = null, editingItem = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            deletingItem = null,
                            userMessage = error.message ?: "Punkt konnte nicht gelöscht werden.",
                        )
                    }
                }
        }
    }

    /** Snackbar im Screen hat die Meldung gezeigt — State wieder leeren (analog ExpensesViewModel). */
    fun consumeUserMessage() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        fun factory(repository: NoteRepository, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    IdeenViewModel(repository, ownPerson) as T
            }
    }
}
