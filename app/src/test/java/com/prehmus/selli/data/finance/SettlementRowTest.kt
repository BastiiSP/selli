package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettlementRowTest {
    @Test
    fun `from and toDomain preserve every field`() {
        val settlement = Settlement(
            id = "settlement-1",
            settledBy = Person.MELLI,
            settledAt = Instant.parse("2026-09-14T18:00:00Z"),
            balanceSnapshot = 20.0,
        )

        val mapped = SettlementRow.from(settlement).toDomain()

        assertEquals(settlement, mapped)
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = SettlementRow(
            id = "settlement-2",
            settledBy = "UNKNOWN",
            settledAt = "2026-09-14T18:00:00Z",
            balanceSnapshot = 5.0,
        )

        assertNull(row.toDomain())
    }
}
