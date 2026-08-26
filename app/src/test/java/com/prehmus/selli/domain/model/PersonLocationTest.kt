package com.prehmus.selli.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonLocationTest {
    private val now = Instant.parse("2026-08-26T14:00:00Z")

    @Test
    fun `freshness is live at zero seconds`() {
        assertEquals(LocationFreshness.LIVE, locationAt(now).freshness(now))
    }

    @Test
    fun `freshness is live at 119 seconds`() {
        assertEquals(LocationFreshness.LIVE, locationAt(now.minusSeconds(119)).freshness(now))
    }

    @Test
    fun `freshness is recent at exactly two minutes`() {
        assertEquals(LocationFreshness.RECENT, locationAt(now.minusSeconds(120)).freshness(now))
    }

    @Test
    fun `freshness is recent at 121 seconds`() {
        assertEquals(LocationFreshness.RECENT, locationAt(now.minusSeconds(121)).freshness(now))
    }

    @Test
    fun `freshness is recent at 29 minutes`() {
        assertEquals(LocationFreshness.RECENT, locationAt(now.minusSeconds(29 * 60)).freshness(now))
    }

    @Test
    fun `freshness is stale at exactly 30 minutes`() {
        assertEquals(LocationFreshness.STALE, locationAt(now.minusSeconds(30 * 60)).freshness(now))
    }

    @Test
    fun `freshness is stale at 31 minutes`() {
        assertEquals(LocationFreshness.STALE, locationAt(now.minusSeconds(31 * 60)).freshness(now))
    }

    @Test
    fun `future timestamp is live`() {
        assertEquals(LocationFreshness.LIVE, locationAt(now.plusSeconds(10)).freshness(now))
    }

    private fun locationAt(updatedAt: Instant) = PersonLocation(
        person = Person.BASTI,
        latitude = 52.52,
        longitude = 13.405,
        accuracyMeters = 8.0,
        speedMetersPerSecond = null,
        isMoving = false,
        updatedAt = updatedAt,
    )
}
