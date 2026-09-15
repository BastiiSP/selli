package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseBalanceTest {
    private fun expense(
        id: String,
        amount: Double,
        paidBy: Person,
        settlementId: String? = null,
    ) = Expense(
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
    fun `only Basti pays, Melli owes half`() {
        val balance = calculateBalance(listOf(expense("1", 40.0, Person.BASTI)))
        assertEquals(20.0, balance, 0.001)
    }

    @Test
    fun `only Melli pays, balance is negative`() {
        val balance = calculateBalance(listOf(expense("1", 30.0, Person.MELLI)))
        assertEquals(-15.0, balance, 0.001)
    }

    @Test
    fun `mixed expenses net out correctly`() {
        val balance = calculateBalance(
            listOf(
                expense("1", 40.0, Person.BASTI),
                expense("2", 30.0, Person.MELLI),
            ),
        )
        assertEquals(5.0, balance, 0.001)
    }

    @Test
    fun `already settled expenses are excluded`() {
        val balance = calculateBalance(
            listOf(
                expense("1", 100.0, Person.BASTI, settlementId = "old-settlement"),
                expense("2", 10.0, Person.MELLI),
            ),
        )
        assertEquals(-5.0, balance, 0.001)
    }

    @Test
    fun `no expenses means settled`() {
        assertEquals(0.0, calculateBalance(emptyList()), 0.001)
        assertEquals(BalanceDirection.SETTLED, balanceDirection(0.0))
    }

    @Test
    fun `balance direction reflects sign`() {
        assertEquals(BalanceDirection.MELLI_OWES_BASTI, balanceDirection(5.0))
        assertEquals(BalanceDirection.BASTI_OWES_MELLI, balanceDirection(-5.0))
        assertEquals(BalanceDirection.SETTLED, balanceDirection(0.001))
    }
}
