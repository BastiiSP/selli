package com.prehmus.selli.ui.location

import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveShareLabelsTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    @Test
    fun `remaining time rounds started minutes up`() {
        assertEquals("endet gleich", formatRemaining(Duration.ZERO))
        assertEquals("endet gleich", formatRemaining(Duration.ofSeconds(-1)))
        assertEquals("noch 1 Min.", formatRemaining(Duration.ofSeconds(59)))
        assertEquals("noch 52 Min.", formatRemaining(Duration.ofMinutes(52)))
        assertEquals("noch 1 Std.", formatRemaining(Duration.ofMinutes(60)))
        assertEquals("noch 1 Std. 5 Min.", formatRemaining(Duration.ofMinutes(65)))
        assertEquals("noch 8 Std.", formatRemaining(Duration.ofHours(8)))
    }

    @Test
    fun `measurement age follows second minute hour and day boundaries`() {
        assertEquals("gerade eben", formatMeasurementAge(Duration.ofSeconds(-1)))
        assertEquals("gerade eben", formatMeasurementAge(Duration.ofSeconds(14)))
        assertEquals("vor 15 s", formatMeasurementAge(Duration.ofSeconds(15)))
        assertEquals("vor 55 s", formatMeasurementAge(Duration.ofSeconds(59)))
        assertEquals("vor 1 Min.", formatMeasurementAge(Duration.ofSeconds(60)))
        assertEquals("vor 59 Min.", formatMeasurementAge(Duration.ofMinutes(59)))
        assertEquals("vor 1 Std.", formatMeasurementAge(Duration.ofHours(1)))
        assertEquals("vor 1 Tag", formatMeasurementAge(Duration.ofDays(1)))
        assertEquals("vor 3 Tagen", formatMeasurementAge(Duration.ofDays(3)))
    }

    @Test
    fun `clock time uses supplied zone`() {
        assertEquals("14:05", formatClockTime(Instant.parse("2026-10-23T12:05:00Z"), ZoneId.of("Europe/Berlin")))
        assertEquals("08:05", formatClockTime(Instant.parse("2026-10-23T12:05:00Z"), ZoneId.of("America/New_York")))
    }

    @Test
    fun `marker describes freshness and active live signal`() {
        assertEquals("gerade eben", location(age = Duration.ofSeconds(10)).markerLabel(now))
        assertEquals("LIVE · gerade eben", location(age = Duration.ofSeconds(10), liveUntil = now.plusSeconds(900)).markerLabel(now))
        assertEquals("vor 20 Min.", location(age = Duration.ofMinutes(20)).markerLabel(now))
        assertEquals("veraltet · vor 50 Min.", location(age = Duration.ofMinutes(50)).markerLabel(now))
        assertEquals(
            "Live-Signal veraltet · vor 6 Min.",
            location(age = Duration.ofMinutes(6), liveUntil = now.plusSeconds(900)).markerLabel(now),
        )
        assertEquals(
            "vor 20 Min.",
            location(age = Duration.ofMinutes(20), liveUntil = now.minusSeconds(1)).markerLabel(now),
        )
    }

    @Test
    fun `imprecision label uses accuracy threshold and kilometres`() {
        assertNull(location(accuracy = null).imprecisionLabel())
        assertNull(location(accuracy = 100.0).imprecisionLabel())
        assertEquals("ungenau (± 300 m)", location(accuracy = 300.0).imprecisionLabel())
        assertEquals("ungenau (± 1,5 km)", location(accuracy = 1_500.0).imprecisionLabel())
    }

    @Test
    fun `person display name and partner match both accounts`() {
        assertEquals("Basti", Person.BASTI.displayName())
        assertEquals(Person.MELLI, Person.BASTI.partner())
        assertEquals("Melli", Person.MELLI.displayName())
        assertEquals(Person.BASTI, Person.MELLI.partner())
    }

    @Test
    fun `duration labels describe all choices`() {
        assertEquals("15 Minuten", LiveShareDuration.MINUTES_15.label())
        assertEquals("1 Stunde", LiveShareDuration.HOUR_1.label())
        assertEquals("8 Stunden", LiveShareDuration.HOURS_8.label())
    }

    private fun location(
        age: Duration = Duration.ZERO,
        liveUntil: Instant? = null,
        accuracy: Double? = 12.0,
    ) = PersonLocation(
        person = Person.BASTI,
        latitude = 52.0,
        longitude = 13.0,
        accuracyMeters = accuracy,
        speedMetersPerSecond = null,
        isMoving = false,
        updatedAt = now.minus(age),
        liveUntil = liveUntil,
    )
}
