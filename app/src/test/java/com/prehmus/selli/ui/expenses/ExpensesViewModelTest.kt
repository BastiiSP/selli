package com.prehmus.selli.ui.expenses

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.ExpenseRepository
import com.prehmus.selli.ui.calendar.MainDispatcherRule
import com.prehmus.selli.ui.components.CloudIssue
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `failed first load leaves expenses and balance untouched`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeExpenses().apply {
            expensesResult = Result.failure(CloudSyncException.Offline())
        }
        val viewModel = ExpensesViewModel(repository, Person.BASTI, FakeSession())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasLoaded)
        assertTrue(viewModel.uiState.value.expenses.isEmpty())
        assertTrue(viewModel.uiState.value.openExpenses.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.balance, 0.0)
        assertEquals(CloudIssue.OFFLINE, viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `failed refresh preserves loaded expenses and balance`() = runTest(mainDispatcherRule.dispatcher) {
        val expense = Expense(
            id = "expense-1",
            amount = 24.0,
            description = "Einkauf",
            paidBy = Person.BASTI,
            createdBy = Person.BASTI,
            spentAt = LocalDate.of(2026, 1, 1),
            createdAt = Instant.EPOCH,
            settlementId = null,
        )
        val repository = FakeExpenses().apply { expensesResult = Result.success(listOf(expense)) }
        val viewModel = ExpensesViewModel(repository, Person.BASTI, FakeSession())
        advanceUntilIdle()
        val previousBalance = viewModel.uiState.value.balance
        assertEquals(12.0, previousBalance, 0.0)

        repository.expensesResult = Result.failure(CloudSyncException.Offline())
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(expense), viewModel.uiState.value.expenses)
        assertEquals(listOf(expense), viewModel.uiState.value.openExpenses)
        assertEquals(previousBalance, viewModel.uiState.value.balance, 0.0)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertEquals(CloudIssue.OFFLINE, viewModel.uiState.value.loadIssue)
    }

    private class FakeSession : CloudSessionRepository {
        override val accessState: StateFlow<CloudAccessState> = MutableStateFlow(CloudAccessState.OFFLINE)
        override suspend fun ensureAccess(): Result<Unit> = Result.success(Unit)
        override suspend fun reconfirmInteractively(): CloudReconfirmResult = CloudReconfirmResult.Success
    }

    private class FakeExpenses : ExpenseRepository {
        var expensesResult: Result<List<Expense>> = Result.success(emptyList())
        override suspend fun loadExpenses(): List<Expense> = expensesResult.getOrDefault(emptyList())
        override suspend fun loadExpensesResult(): Result<List<Expense>> = expensesResult
        override suspend fun loadSettlements(): List<Settlement> = emptyList()
        override suspend fun loadSettlementsResult(): Result<List<Settlement>> = Result.success(emptyList())
        override suspend fun addExpense(
            amount: Double,
            description: String,
            paidBy: Person,
            createdBy: Person,
            spentAt: LocalDate,
        ): Result<Expense> = Result.failure(UnsupportedOperationException())
        override suspend fun updateExpense(
            id: String,
            amount: Double,
            description: String,
            paidBy: Person,
            spentAt: LocalDate,
        ): Result<Unit> = Result.success(Unit)
        override suspend fun deleteExpense(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun settle(settledBy: Person): Result<Unit> = Result.success(Unit)
    }
}
