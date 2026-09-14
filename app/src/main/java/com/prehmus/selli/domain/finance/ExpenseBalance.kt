package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person

/**
 * Laufender Saldo aus allen offenen (noch nicht abgerechneten) Ausgaben. Jede Ausgabe wird
 * zur Hälfte der jeweils anderen Person zugeordnet (feste 50/50-Aufteilung, siehe Spec).
 *
 * Vorzeichenkonvention: positiv -> Melli schuldet Basti [amount] €.
 * negativ -> Basti schuldet Melli [amount] €. Null -> ausgeglichen.
 */
fun calculateBalance(expenses: List<Expense>): Double {
    val open = expenses.filter { it.settlementId == null }
    val paidByBasti = open.filter { it.paidBy == Person.BASTI }.sumOf { it.amount }
    val paidByMelli = open.filter { it.paidBy == Person.MELLI }.sumOf { it.amount }
    return (paidByBasti - paidByMelli) / 2
}

/** Wer aktuell wem schuldet — abgeleitet aus [calculateBalance] für die UI. */
enum class BalanceDirection { BASTI_OWES_MELLI, MELLI_OWES_BASTI, SETTLED }

fun balanceDirection(balance: Double): BalanceDirection = when {
    balance > BALANCE_EPSILON -> BalanceDirection.MELLI_OWES_BASTI
    balance < -BALANCE_EPSILON -> BalanceDirection.BASTI_OWES_MELLI
    else -> BalanceDirection.SETTLED
}

// Rundungsfehler bei Centbeträgen (z.B. 0.1 + 0.2) sollen nicht als "offener Saldo" durchgehen.
private const val BALANCE_EPSILON = 0.005
