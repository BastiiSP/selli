package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.Instant

/** Ergebnis eines Ausgleichs: die neue Settlement-Zeile plus die IDs der jetzt abgerechneten Ausgaben. */
data class SettlementResult(
    val settlement: Settlement,
    val settledExpenseIds: List<String>,
)

/**
 * Reine Berechnung dessen, was beim Ausgleichen passieren soll — welche Ausgaben-IDs auf die
 * neue Settlement-ID gestempelt werden und welchen Saldo-Snapshot das Settlement bekommt.
 * Das eigentliche Schreiben (Update + Insert) übernimmt das Repository (Task 4).
 */
fun prepareSettlement(
    expenses: List<Expense>,
    settledBy: Person,
    settlementId: String,
    now: Instant,
): SettlementResult {
    val open = expenses.filter { it.settlementId == null }
    val balance = calculateBalance(open)
    return SettlementResult(
        settlement = Settlement(
            id = settlementId,
            settledBy = settledBy,
            settledAt = now,
            balanceSnapshot = balance,
        ),
        settledExpenseIds = open.map { it.id },
    )
}
