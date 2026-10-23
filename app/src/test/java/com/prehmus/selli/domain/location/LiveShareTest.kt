package com.prehmus.selli.domain.location

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveShareTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    @Test
    fun `all durations start live and end at the exact deadline`() {
        val minutes = listOf(15L, 60L, 480L)
        LiveShareDuration.entries.zip(minutes).forEach { (duration, length) ->
            val share = startLiveShare(now, duration)
            assertEquals(now, share.startedAt)
            assertEquals(now.plusSeconds(length * 60), share.endsAt)
            assertEquals(SharingMode.LIVE, sharingModeAt(share, now))
            assertTrue(share.isActive(share.endsAt.minusNanos(1)))
            assertFalse(share.isActive(share.endsAt))
            assertEquals(Duration.ZERO, share.remaining(share.endsAt.plusSeconds(1)))
            assertNull(share.validAt(share.endsAt))
            assertEquals(SharingMode.NORMAL, sharingModeAt(share, share.endsAt))
        }
    }

    @Test
    fun `clock rollback tolerance is one minute`() {
        val share = startLiveShare(now, LiveShareDuration.HOURS_8)
        assertEquals(share, share.validAt(now.minusSeconds(60)))
        assertNull(share.validAt(now.minusSeconds(60).minusNanos(1)))
        assertNull(null.validAt(now))
        assertEquals(SharingMode.NORMAL, sharingModeAt(null, now))
    }
}
