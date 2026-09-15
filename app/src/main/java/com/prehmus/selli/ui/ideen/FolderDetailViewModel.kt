package com.prehmus.selli.ui.ideen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

data class FolderDetailUiState(
    val folderName: String = "",
    val openItems: List<NoteItem> = emptyList(),
    val archivedItems: List<NoteItem> = emptyList(),
    val isArchiveExpanded: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAddItemSheetOpen: Boolean = false,
    val editingItem: NoteItem? = null,
    val isSavingItem: Boolean = false,
    val userMessage: String? = null,
)

/**
 * Der Ordnername kommt bewusst nicht als Navigations-Argument (Ordnernamen können
 * URL-unsichere Zeichen enthalten), sondern wird in [refresh] durch Abgleich mit der
 * geladenen Ordnerliste aufgelöst.
 *
 * Kein Realtime-Sync (siehe Spec): [refresh] wird beim Betreten des Screens (siehe
 * `LifecycleResumeEffect` in `FolderDetailScreen`), per Pull-to-Refresh und nach jeder
 * eigenen Änderung aufgerufen.
 */
class FolderDetailViewModel(
    private val repository: NoteRepository,
    private val folderId: String,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FolderDetailUiState())
    val uiState: StateFlow<FolderDetailUiState> = _uiState.asStateFlow()

    // Ein erneuter Aufruf (z. B. Pull-to-Refresh während des Betretens des Screens) darf sich
    // nicht mit einem laufenden Refresh überlappen — sonst kann eine ältere, langsamere Antwort
    // eine frischere überschreiben (analog CalendarViewModel.refreshJob/ExpensesViewModel).
    private var refreshJob: Job? = null

    init { refresh() }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val folder = repository.loadFolders().firstOrNull { it.id == folderId }
            val items = repository.loadItems(folderId)
            _uiState.update {
                it.copy(
                    folderName = folder?.name ?: it.folderName,
                    openItems = items.openItems(),
                    archivedItems = items.archivedItems(),
                    isRefreshing = false,
                )
            }
        }
    }

    fun toggleArchiveExpanded() {
        _uiState.update { it.copy(isArchiveExpanded = !it.isArchiveExpanded) }
    }

    fun openAddItemSheet() { _uiState.update { it.copy(isAddItemSheetOpen = true) } }
    fun dismissAddItemSheet() { _uiState.update { it.copy(isAddItemSheetOpen = false) } }

    fun addItem(text: String, url: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingItem = true) }
            repository.addItem(folderId = folderId, text = text, url = url, createdBy = ownPerson)
                .onSuccess {
                    _uiState.update { it.copy(isSavingItem = false, isAddItemSheetOpen = false) }
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

    fun beginEditing(item: NoteItem) { _uiState.update { it.copy(editingItem = item) } }
    fun dismissEditing() { _uiState.update { it.copy(editingItem = null) } }

    fun saveEdit(text: String, url: String?) {
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

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Punkt konnte nicht gelöscht werden.")
                    }
                }
        }
    }

    /** Snackbar im Screen hat die Meldung gezeigt — State wieder leeren (analog ExpensesViewModel). */
    fun consumeUserMessage() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        fun factory(repository: NoteRepository, folderId: String, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FolderDetailViewModel(repository, folderId, ownPerson) as T
            }
    }
}
