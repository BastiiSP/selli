package com.prehmus.selli.data.finance

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.data.supabase.classifyCloudError
import com.prehmus.selli.data.supabase.logCloudError
import com.prehmus.selli.domain.finance.prepareSettlement
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
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

    // Kompatibilitätsmethode; neue Aufrufer verwenden den fehlererhaltenden Result-Vertrag.
    override suspend fun loadExpenses(): List<Expense> = loadExpensesResult().getOrElse { emptyList() }

    override suspend fun loadExpensesResult(): Result<List<Expense>> = client.withSession { supabase ->
        supabase.from(EXPENSES_TABLE)
            .select { order("created_at", order = Order.DESCENDING) }
            .decodeList<ExpenseRow>()
            .mapNotNull(ExpenseRow::toDomain)
    }

    override suspend fun loadSettlements(): List<Settlement> =
        loadSettlementsResult().getOrElse { emptyList() }

    override suspend fun loadSettlementsResult(): Result<List<Settlement>> = client.withSession { supabase ->
        supabase.from(SETTLEMENTS_TABLE)
            .select { order("settled_at", order = Order.DESCENDING) }
            .decodeList<SettlementRow>()
            .mapNotNull(SettlementRow::toDomain)
    }

    override suspend fun addExpense(
        amount: Double,
        description: String,
        paidBy: Person,
        createdBy: Person,
        spentAt: LocalDate,
    ): Result<Expense> = client.withSession { supabase ->
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
    ): Result<Unit> = client.withSession { supabase ->
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

    override suspend fun deleteExpense(id: String): Result<Unit> = client.withSession { supabase ->
        supabase.from(EXPENSES_TABLE).delete { filter { eq("id", id) } }
        Unit
    }.onFailure { error -> logError(SOURCE_DELETE, error) }

    override suspend fun settle(settledBy: Person): Result<Unit> {
        return try {
            // Ladefehler dürfen niemals als "keine offenen Ausgaben" gelten.
            val openExpenses = loadExpensesResult().getOrThrow().filter { it.settlementId == null }
            val result = prepareSettlement(
                expenses = openExpenses,
                settledBy = settledBy,
                settlementId = UUID.randomUUID().toString(),
                now = Instant.now(),
            )
            if (result.settledExpenseIds.isEmpty()) return Result.success(Unit)

            // Jede Anfrage einzeln wiederholen: ein 401 beim Update darf das bereits angelegte
            // Settlement nicht erneut einfügen. Das bestehende zweistufige Datenmodell bleibt.
            client.withSession { supabase ->
                supabase.from(SETTLEMENTS_TABLE).insert(SettlementRow.from(result.settlement))
            }.getOrThrow()
            client.withSession { supabase ->
                supabase.from(EXPENSES_TABLE).update(
                    { set("settlement_id", result.settlement.id) },
                ) {
                    filter { isIn("id", result.settledExpenseIds) }
                }
                Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_SETTLE, error)
            Result.failure(classifyCloudError(error))
        }
    }

    private fun logError(source: String, error: Throwable) {
        logCloudError(logger, source, error)
    }

    private companion object {
        const val EXPENSES_TABLE = "expenses"
        const val SETTLEMENTS_TABLE = "settlements"
        const val SOURCE_INSERT = "Supabase-Ausgabe anlegen"
        const val SOURCE_UPDATE = "Supabase-Ausgabe bearbeiten"
        const val SOURCE_DELETE = "Supabase-Ausgabe löschen"
        const val SOURCE_SETTLE = "Supabase-Ausgleichen"
    }
}
