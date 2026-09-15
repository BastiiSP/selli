package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementCalculationTest {
    private fun expense(id: String, amount: Double, paidBy: Person, settlementId: String? = null) = Expense(
        id = id,
        amount = amount,
        description = "Testausgabe",
        paidBy = paidBy,
        createdBy = paidBy,
        spentAt = LocalDate.of(2026, 9, 14),
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        settlementId = settlementId,
    )

    @Test
    fun `prepareSettlement covers only currently open expenses`() {
        val expenses = listOf(
            expense("1", 40.0, Person.BASTI),
            expense("2", 10.0, Person.MELLI, settlementId = "already-settled"),
        )
        val result = prepareSettlement(
            expenses = expenses,
            settledBy = Person.MELLI,
            settlementId = "new-settlement",
            now = Instant.parse("2026-09-14T12:00:00Z"),
        )

        assertEquals(listOf("1"), result.settledExpenseIds)
        assertEquals(20.0, result.settlement.balanceSnapshot, 0.001)
        assertEquals("new-settlement", result.settlement.id)
        assertEquals(Person.MELLI, result.settlement.settledBy)
    }

    @Test
    fun `prepareSettlement with no open expenses yields zero balance and empty id list`() {
        val result = prepareSettlement(
            expenses = listOf(expense("1", 40.0, Person.BASTI, settlementId = "old")),
            settledBy = Person.BASTI,
            settlementId = "new",
            now = Instant.parse("2026-09-14T12:00:00Z"),
        )

        assertEquals(emptyList<String>(), result.settledExpenseIds)
        assertEquals(0.0, result.settlement.balanceSnapshot, 0.001)
    }
}
