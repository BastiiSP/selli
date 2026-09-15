package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SettlementRow(
    @SerialName("id") val id: String,
    @SerialName("settled_by") val settledBy: String,
    @SerialName("settled_at") val settledAt: String,
    @SerialName("balance_snapshot") val balanceSnapshot: Double,
) {
    companion object
}

fun SettlementRow.toDomain(): Settlement? {
    val mappedPerson = runCatching { Person.valueOf(settledBy) }.getOrNull() ?: return null
    val mappedSettledAt = runCatching { OffsetDateTime.parse(settledAt).toInstant() }.getOrNull()
        ?: return null

    return Settlement(
        id = id,
        settledBy = mappedPerson,
        settledAt = mappedSettledAt,
        balanceSnapshot = balanceSnapshot,
    )
}

fun SettlementRow.Companion.from(settlement: Settlement): SettlementRow = SettlementRow(
    id = settlement.id,
    settledBy = settlement.settledBy.name,
    settledAt = DateTimeFormatter.ISO_INSTANT.format(settlement.settledAt),
    balanceSnapshot = settlement.balanceSnapshot,
)
