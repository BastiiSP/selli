package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NewExpenseDetectorTest {
    private fun expense(id: String, paidBy: Person, createdBy: Person) = Expense(
        id = id,
        amount = 10.0,
        description = "Test",
        paidBy = paidBy,
        createdBy = createdBy,
        spentAt = LocalDate.of(2026, 9, 14),
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        settlementId = null,
    )

    @Test
    fun `expense created by partner is reported as new`() {
        val result = detectNewExpenses(
            currentExpenses = listOf(expense("1", Person.MELLI, Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(listOf("1"), result.newExpenses.map { it.id })
        assertEquals(setOf("1"), result.updatedSeenIds)
    }

    @Test
    fun `own expense is never reported, even when paid by partner`() {
        // Stellvertretendes Eintragen: Basti trägt eine Ausgabe ein, die Melli bezahlt hat.
        val result = detectNewExpenses(
            currentExpenses = listOf(expense("1", Person.MELLI, Person.BASTI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(emptyList<Expense>(), result.newExpenses)
    }

    @Test
    fun `already seen expense is not reported again`() {
        val result = detectNewExpenses(
            currentExpenses = listOf(expense("1", Person.MELLI, Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = setOf("1"),
        )

        assertEquals(emptyList<Expense>(), result.newExpenses)
        assertEquals(setOf("1"), result.updatedSeenIds)
    }
}
