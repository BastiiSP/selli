package com.prehmus.selli.domain.model

import java.time.Instant
import java.time.LocalDate

data class Expense(
    val id: String,
    val amount: Double,
    val description: String,
    val paidBy: Person,
    val createdBy: Person,
    val spentAt: LocalDate,
    val createdAt: Instant,
    val settlementId: String?,
)

data class Settlement(
    val id: String,
    val settledBy: Person,
    val settledAt: Instant,
    val balanceSnapshot: Double,
)
