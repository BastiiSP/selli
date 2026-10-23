package com.prehmus.selli.domain.location

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCadenceTest {
    @Test
    fun `normal and live use exact request parameters`() {
        assertEquals(CadenceSettings(900_000L, 600_000L, 0f, 0L, false), SharingMode.NORMAL.cadence())
        assertEquals(CadenceSettings(10_000L, 5_000L, 0f, 0L, true), SharingMode.LIVE.cadence())
    }

    @Test
    fun `normal uploads every fix and live uploads its first fix`() {
        assertTrue(shouldUploadFix(SharingMode.NORMAL, 0L, 0f, null))
        assertTrue(shouldUploadFix(SharingMode.LIVE, null, null, null))
        assertTrue(shouldUploadFix(SharingMode.LIVE, null, 0f, 300f))
        assertTrue(shouldUploadFix(SharingMode.LIVE, 0L, null, 300f))
    }

    @Test
    fun `live spacing and movement thresholds are inclusive`() {
        assertFalse(shouldUploadFix(SharingMode.LIVE, 9_999L, 200f, 5f))
        assertTrue(shouldUploadFix(SharingMode.LIVE, 10_000L, 20f, 5f))
        assertFalse(shouldUploadFix(SharingMode.LIVE, 10_000L, 19.9f, 5f))
        assertFalse(shouldUploadFix(SharingMode.LIVE, 10_000L, 49.9f, 50f))
        assertTrue(shouldUploadFix(SharingMode.LIVE, 10_000L, 50f, 50f))
        assertTrue(shouldUploadFix(SharingMode.LIVE, 10_000L, 100f, 100f))
    }

    @Test
    fun `imprecise or unknown accuracy only uploads on heartbeat`() {
        for (accuracy in listOf(null, 100.1f, 300f)) {
            assertFalse(shouldUploadFix(SharingMode.LIVE, 59_999L, 1_000f, accuracy))
            assertTrue(shouldUploadFix(SharingMode.LIVE, 60_000L, 0f, accuracy))
        }
        assertFalse(shouldUploadFix(SharingMode.LIVE, 59_999L, 0f, 5f))
        assertTrue(shouldUploadFix(SharingMode.LIVE, 60_000L, 0f, 5f))
    }

    @Test
    fun `feed polls faster only for valid active live positions`() {
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val point = PersonLocation(Person.BASTI, 0.0, 0.0, null, null, false, now)
        assertEquals(60_000L, feedPollingMillis(emptyList(), now))
        assertEquals(60_000L, feedPollingMillis(listOf(point), now))
        assertEquals(15_000L, feedPollingMillis(listOf(point, point.copy(liveUntil = now.plusSeconds(1))), now))
        assertEquals(60_000L, feedPollingMillis(listOf(point.copy(liveUntil = now)), now))
        assertEquals(60_000L, feedPollingMillis(listOf(point.copy(liveUntil = now.plusSeconds(30_000))), now))
    }
}
