package com.prehmus.selli.ui.ideen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeenUiState(
    val folders: List<NoteFolder> = emptyList(),
    val openCountByFolder: Map<String, Int> = emptyMap(),
    val totalCountByFolder: Map<String, Int> = emptyMap(),
    val isRefreshing: Boolean = false,
    val isCreateFolderSheetOpen: Boolean = false,
    val renamingFolder: NoteFolder? = null,
    val deletingFolder: NoteFolder? = null,
)

/**
 * Kein Realtime-Sync (siehe Spec): [refresh] wird beim Öffnen des Tabs, per
 * Pull-to-Refresh und nach jeder eigenen Änderung aufgerufen.
 */
class IdeenViewModel(
    private val repository: NoteRepository,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IdeenUiState())
    val uiState: StateFlow<IdeenUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val folders = repository.loadFolders()
            val openCounts = mutableMapOf<String, Int>()
            val totalCounts = mutableMapOf<String, Int>()
            folders.forEach { folder ->
                val items = repository.loadItems(folder.id)
                openCounts[folder.id] = items.count { !it.isChecked }
                totalCounts[folder.id] = items.size
            }
            _uiState.update {
                it.copy(
                    folders = folders,
                    openCountByFolder = openCounts,
                    totalCountByFolder = totalCounts,
                    isRefreshing = false,
                )
            }
        }
    }

    fun openCreateFolderSheet() { _uiState.update { it.copy(isCreateFolderSheetOpen = true) } }
    fun dismissCreateFolderSheet() { _uiState.update { it.copy(isCreateFolderSheetOpen = false) } }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.addFolder(name = name, createdBy = ownPerson)
            _uiState.update { it.copy(isCreateFolderSheetOpen = false) }
            refresh()
        }
    }

    fun beginRenaming(folder: NoteFolder) { _uiState.update { it.copy(renamingFolder = folder) } }
    fun dismissRenaming() { _uiState.update { it.copy(renamingFolder = null) } }

    fun renameFolder(name: String) {
        val folder = _uiState.value.renamingFolder ?: return
        viewModelScope.launch {
            repository.renameFolder(id = folder.id, name = name)
            _uiState.update { it.copy(renamingFolder = null) }
            refresh()
        }
    }

    fun beginDeleting(folder: NoteFolder) { _uiState.update { it.copy(deletingFolder = folder) } }
    fun dismissDeleting() { _uiState.update { it.copy(deletingFolder = null) } }

    fun confirmDelete() {
        val folder = _uiState.value.deletingFolder ?: return
        viewModelScope.launch {
            repository.deleteFolder(folder.id)
            _uiState.update { it.copy(deletingFolder = null) }
            refresh()
        }
    }

    companion object {
        fun factory(repository: NoteRepository, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    IdeenViewModel(repository, ownPerson) as T
            }
    }
}
