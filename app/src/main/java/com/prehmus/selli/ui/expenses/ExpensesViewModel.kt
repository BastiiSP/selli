package com.prehmus.selli.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.finance.BalanceDirection
import com.prehmus.selli.domain.finance.balanceDirection
import com.prehmus.selli.domain.finance.calculateBalance
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.ExpenseRepository
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val balance: Double = 0.0,
    val balanceDirection: BalanceDirection = BalanceDirection.SETTLED,
    val isRefreshing: Boolean = false,
    val isCreateSheetOpen: Boolean = false,
    val editingExpense: Expense? = null,
    val userMessage: String? = null,
)

/**
 * Kein Realtime-Sync (siehe Spec): [refresh] wird beim Öffnen des Tabs, per
 * Pull-to-Refresh und nach jeder eigenen Änderung aufgerufen.
 */
class ExpensesViewModel(
    private val repository: ExpenseRepository,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    // Ein manuelles Pull-to-Refresh während ein Refresh noch läuft darf sich nicht stapeln:
    // Jeder neue Refresh bricht den vorherigen ab, damit eine ältere, langsamere Antwort nicht
    // die frische überschreibt (analog zu CalendarViewModel.refreshJob).
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val expenses = repository.loadExpenses()
            val balance = calculateBalance(expenses)
            _uiState.update {
                it.copy(
                    expenses = expenses,
                    balance = balance,
                    balanceDirection = balanceDirection(balance),
                    isRefreshing = false,
                )
            }
        }
    }

    fun openCreateSheet() {
        _uiState.update { it.copy(isCreateSheetOpen = true) }
    }

    fun dismissCreateSheet() {
        _uiState.update { it.copy(isCreateSheetOpen = false) }
    }

    fun addExpense(amount: Double, description: String, paidBy: Person, spentAt: LocalDate) {
        viewModelScope.launch {
            repository.addExpense(
                amount = amount,
                description = description,
                paidBy = paidBy,
                createdBy = ownPerson,
                spentAt = spentAt,
            ).onSuccess {
                _uiState.update { it.copy(isCreateSheetOpen = false) }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = error.message ?: "Ausgabe konnte nicht gespeichert werden.")
                }
            }
        }
    }

    fun beginEditing(expense: Expense) {
        _uiState.update { it.copy(editingExpense = expense) }
    }

    fun dismissEditing() {
        _uiState.update { it.copy(editingExpense = null) }
    }

    fun saveEdit(amount: Double, description: String, paidBy: Person, spentAt: LocalDate) {
        val editing = _uiState.value.editingExpense ?: return
        viewModelScope.launch {
            repository.updateExpense(
                id = editing.id,
                amount = amount,
                description = description,
                paidBy = paidBy,
                spentAt = spentAt,
            ).onSuccess {
                _uiState.update { it.copy(editingExpense = null) }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = error.message ?: "Änderung konnte nicht gespeichert werden.")
                }
            }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            repository.deleteExpense(id)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Ausgabe konnte nicht gelöscht werden.")
                    }
                }
        }
    }

    fun settle() {
        viewModelScope.launch {
            repository.settle(settledBy = ownPerson)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Ausgleichen ist fehlgeschlagen.")
                    }
                }
        }
    }

    /** Snackbar im Screen hat die Meldung gezeigt — State wieder leeren (analog CalendarViewModel). */
    fun consumeUserMessage() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        fun factory(repository: ExpenseRepository, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpensesViewModel(repository, ownPerson) as T
            }
    }
}
