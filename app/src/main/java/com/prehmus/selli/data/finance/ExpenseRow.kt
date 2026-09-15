package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseRow(
    @SerialName("id") val id: String,
    @SerialName("amount") val amount: Double,
    @SerialName("description") val description: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("spent_at") val spentAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("settlement_id") val settlementId: String? = null,
) {
    companion object
}

fun ExpenseRow.toDomain(): Expense? {
    val mappedPaidBy = runCatching { Person.valueOf(paidBy) }.getOrNull() ?: return null
    val mappedCreatedBy = runCatching { Person.valueOf(createdBy) }.getOrNull() ?: return null
    val mappedSpentAt = runCatching { LocalDate.parse(spentAt) }.getOrNull() ?: return null
    val mappedCreatedAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: return null

    return Expense(
        id = id,
        amount = amount,
        description = description,
        paidBy = mappedPaidBy,
        createdBy = mappedCreatedBy,
        spentAt = mappedSpentAt,
        createdAt = mappedCreatedAt,
        settlementId = settlementId,
    )
}

fun ExpenseRow.Companion.from(expense: Expense): ExpenseRow = ExpenseRow(
    id = expense.id,
    amount = expense.amount,
    description = expense.description,
    paidBy = expense.paidBy.name,
    createdBy = expense.createdBy.name,
    spentAt = expense.spentAt.toString(),
    createdAt = DateTimeFormatter.ISO_INSTANT.format(expense.createdAt),
    settlementId = expense.settlementId,
)
