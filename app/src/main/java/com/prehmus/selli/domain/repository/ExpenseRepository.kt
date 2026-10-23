package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.LocalDate

/** Result-Fehler sind CloudSyncException; Coroutine-Abbruch wird durchgereicht. */
interface ExpenseRepository {
    /** Alle Ausgaben, neueste zuerst. Liefert bei Problemen eine leere Liste statt zu werfen. */
    suspend fun loadExpenses(): List<Expense>

    /** Ladefehler bleiben als CloudSyncException erkennbar. */
    suspend fun loadExpensesResult(): Result<List<Expense>>

    /** Neue Ausgabe anlegen. Wirft nie, meldet Fehler über [Result]. */
    suspend fun addExpense(
        amount: Double,
        description: String,
        paidBy: Person,
        createdBy: Person,
        spentAt: LocalDate,
    ): Result<Expense>

    suspend fun updateExpense(
        id: String,
        amount: Double,
        description: String,
        paidBy: Person,
        spentAt: LocalDate,
    ): Result<Unit>

    suspend fun deleteExpense(id: String): Result<Unit>

    /** Gleicht alle offenen Ausgaben aus (stempelt sie auf ein neues Settlement). */
    suspend fun settle(settledBy: Person): Result<Unit>

    /** Alle Ausgleichsvorgänge, neueste zuerst. Liefert bei Problemen eine leere Liste statt zu werfen. */
    suspend fun loadSettlements(): List<Settlement>

    /** Ladefehler bleiben als CloudSyncException erkennbar. */
    suspend fun loadSettlementsResult(): Result<List<Settlement>>
}
