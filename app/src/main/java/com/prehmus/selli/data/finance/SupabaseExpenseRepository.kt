package com.prehmus.selli.data.finance

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.domain.finance.prepareSettlement
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.ExpenseRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException

class SupabaseExpenseRepository(
    private val client: SelliSupabaseClient,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : ExpenseRepository {

    override suspend fun loadExpenses(): List<Expense> {
        if (!client.isConfigured) return emptyList()
        if (client.ensureSignedIn().isFailure) return emptyList()
        val supabase = client.client ?: return emptyList()

        return try {
            supabase.from(EXPENSES_TABLE)
                .select { order("created_at", order = Order.DESCENDING) }
                .decodeList<ExpenseRow>()
                .mapNotNull(ExpenseRow::toDomain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_SELECT, error)
            emptyList()
        }
    }

    override suspend fun addExpense(
        amount: Double,
        description: String,
        paidBy: Person,
        createdBy: Person,
        spentAt: LocalDate,
    ): Result<Expense> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            description = description,
            paidBy = paidBy,
            createdBy = createdBy,
            spentAt = spentAt,
            createdAt = Instant.now(),
            settlementId = null,
        )
        supabase.from(EXPENSES_TABLE).insert(ExpenseRow.from(expense))
        expense
    }.onFailure { error -> logError(SOURCE_INSERT, error) }

    override suspend fun updateExpense(
        id: String,
        amount: Double,
        description: String,
        paidBy: Person,
        spentAt: LocalDate,
    ): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(EXPENSES_TABLE).update(
            {
                set("amount", amount)
                set("description", description)
                set("paid_by", paidBy.name)
                set("spent_at", spentAt.toString())
            },
        ) {
            filter { eq("id", id) }
        }
        Unit
    }.onFailure { error -> logError(SOURCE_UPDATE, error) }

    override suspend fun deleteExpense(id: String): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(EXPENSES_TABLE).delete { filter { eq("id", id) } }
        Unit
    }.onFailure { error -> logError(SOURCE_DELETE, error) }

    override suspend fun settle(settledBy: Person): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        val openExpenses = loadExpenses().filter { it.settlementId == null }
        val result = prepareSettlement(
            expenses = openExpenses,
            settledBy = settledBy,
            settlementId = UUID.randomUUID().toString(),
            now = Instant.now(),
        )
        if (result.settledExpenseIds.isEmpty()) return@runCatching

        supabase.from(SETTLEMENTS_TABLE).insert(SettlementRow.from(result.settlement))
        supabase.from(EXPENSES_TABLE).update(
            { set("settlement_id", result.settlement.id) },
        ) {
            filter { isIn("id", result.settledExpenseIds) }
        }
        Unit
    }.onFailure { error -> logError(SOURCE_SETTLE, error) }

    private fun logError(source: String, error: Throwable) {
        runCatching { logger.error(source, error) }
    }

    private companion object {
        const val EXPENSES_TABLE = "expenses"
        const val SETTLEMENTS_TABLE = "settlements"
        const val SOURCE_SELECT = "Supabase-Ausgaben laden"
        const val SOURCE_INSERT = "Supabase-Ausgabe anlegen"
        const val SOURCE_UPDATE = "Supabase-Ausgabe bearbeiten"
        const val SOURCE_DELETE = "Supabase-Ausgabe löschen"
        const val SOURCE_SETTLE = "Supabase-Ausgleichen"
    }
}
