package com.prehmus.selli.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonLocationTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val point = PersonLocation(Person.BASTI, 50.0, 10.0, 8.0, null, false, now)

    @Test
    fun `normal freshness uses measurement age and exact boundaries`() {
        assertEquals(LocationFreshness.JUST_NOW, point.freshness(now))
        assertEquals(LocationFreshness.JUST_NOW, point.freshness(now.plusSeconds(119)))
        assertEquals(LocationFreshness.RECENT, point.freshness(now.plusSeconds(120)))
        assertEquals(LocationFreshness.RECENT, point.freshness(now.plusSeconds(2_699)))
        assertEquals(LocationFreshness.STALE, point.freshness(now.plusSeconds(2_700)))
        assertEquals(LocationFreshness.JUST_NOW, point.freshness(now.minusSeconds(1)))
        assertEquals(Duration.ZERO, point.measurementAge(now.minusSeconds(1)))
        assertEquals(Duration.ofSeconds(40), point.measurementAge(now.plusSeconds(40)))
    }

    @Test
    fun `live signal is stale after two minutes`() {
        val live = point.copy(liveUntil = now.plusSeconds(900))
        assertEquals(LocationFreshness.JUST_NOW, live.freshness(now.plusSeconds(119)))
        assertEquals(LocationFreshness.STALE, live.freshness(now.plusSeconds(120)))
        assertEquals(LocationFreshness.RECENT, live.freshness(now.plusSeconds(900)))
    }

    @Test
    fun `live ends strictly after now and allows at most eight hours plus five minutes`() {
        assertFalse(point.isLiveSharing(now))
        assertFalse(point.copy(liveUntil = now).isLiveSharing(now))
        assertFalse(point.copy(liveUntil = now.minusNanos(1)).isLiveSharing(now))
        assertTrue(point.copy(liveUntil = now.plusNanos(1)).isLiveSharing(now))
        val upper = now.plus(Duration.ofHours(8)).plusSeconds(300)
        assertTrue(point.copy(liveUntil = upper).isLiveSharing(now))
        assertFalse(point.copy(liveUntil = upper.plusNanos(1)).isLiveSharing(now))
        assertNull(point.liveRemaining(now))
        assertNull(point.copy(liveUntil = now).liveRemaining(now))
        assertNull(point.copy(liveUntil = upper.plusNanos(1)).liveRemaining(now))
        assertEquals(Duration.ofMinutes(15), point.copy(liveUntil = now.plusSeconds(900)).liveRemaining(now))
    }

    @Test
    fun `only known accuracy over one hundred meters is imprecise`() {
        assertFalse(point.copy(accuracyMeters = null).isImprecise())
        assertFalse(point.copy(accuracyMeters = 100.0).isImprecise())
        assertTrue(point.copy(accuracyMeters = 100.01).isImprecise())
    }
}
