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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
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
            )
            _uiState.update { it.copy(isCreateSheetOpen = false) }
            refresh()
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
            )
            _uiState.update { it.copy(editingExpense = null) }
            refresh()
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            refresh()
        }
    }

    fun settle() {
        viewModelScope.launch {
            repository.settle(settledBy = ownPerson)
            refresh()
        }
    }

    companion object {
        fun factory(repository: ExpenseRepository, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpensesViewModel(repository, ownPerson) as T
            }
    }
}
