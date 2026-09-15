package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person

data class NewExpenseDetectionResult(
    val newExpenses: List<Expense>,
    val updatedSeenIds: Set<String>,
)

/**
 * Erkennt Ausgaben, die der Partner neu angelegt hat und die noch nicht gemeldet wurden.
 * Reine Funktion, kein Netzwerk. Vergleicht über [Expense.createdBy] (wer eingetragen hat),
 * nicht über [Expense.paidBy] (wer bezahlt hat) — beim stellvertretenden Eintragen können
 * beide auseinanderfallen (siehe Migration Task 3, `created_by`-Spalte).
 */
fun detectNewExpenses(
    currentExpenses: List<Expense>,
    self: Person,
    alreadySeenIds: Set<String>,
): NewExpenseDetectionResult {
    val newFromPartner = currentExpenses.filter { expense ->
        expense.createdBy != self && expense.id !in alreadySeenIds
    }
    return NewExpenseDetectionResult(
        newExpenses = newFromPartner,
        updatedSeenIds = currentExpenses.map { it.id }.toSet(),
    )
}
