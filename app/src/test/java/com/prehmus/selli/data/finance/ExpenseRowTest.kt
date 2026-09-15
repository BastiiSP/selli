package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseRowTest {
    @Test
    fun `from and toDomain preserve every field`() {
        val expense = Expense(
            id = "expense-1",
            amount = 24.5,
            description = "Einkauf",
            paidBy = Person.BASTI,
            createdBy = Person.MELLI,
            spentAt = LocalDate.of(2026, 9, 14),
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
            settlementId = null,
        )

        val mapped = ExpenseRow.from(expense).toDomain()

        assertEquals(expense, mapped)
    }

    @Test
    fun `settlementId round-trips when set`() {
        val expense = Expense(
            id = "expense-2",
            amount = 10.0,
            description = "Tanken",
            paidBy = Person.MELLI,
            createdBy = Person.MELLI,
            spentAt = LocalDate.of(2026, 9, 1),
            createdAt = Instant.parse("2026-09-01T08:00:00Z"),
            settlementId = "settlement-1",
        )

        val mapped = ExpenseRow.from(expense).toDomain()

        assertEquals("settlement-1", mapped?.settlementId)
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = ExpenseRow(
            id = "expense-3",
            amount = 5.0,
            description = "Kaputt",
            paidBy = "UNKNOWN",
            createdBy = "BASTI",
            spentAt = "2026-09-14",
            createdAt = "2026-09-14T10:00:00Z",
        )

        assertNull(row.toDomain())
    }
}
