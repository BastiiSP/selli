package com.prehmus.selli.domain.format

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTimeFormatterTest {
    @Test
    fun `formats same-day timed event as times only`() {
        assertEquals(
            "18:00 – 19:00",
            EventTimeFormatter.formatRange(
                start = LocalDateTime.of(2025, 8, 1, 18, 0),
                end = LocalDateTime.of(2025, 8, 1, 19, 0),
                isAllDay = false,
            ),
        )
    }

    @Test
    fun `formats multi-day timed event with start and end dates`() {
        assertEquals(
            "Fr., 1. Aug. 18:00 – So., 3. Aug. 10:00",
            EventTimeFormatter.formatRange(
                start = LocalDateTime.of(2025, 8, 1, 18, 0),
                end = LocalDateTime.of(2025, 8, 3, 10, 0),
                isAllDay = false,
            ),
        )
    }

    @Test
    fun `formats one-day all-day event without dates`() {
        assertEquals(
            "ganztägig",
            EventTimeFormatter.formatRange(
                start = LocalDateTime.of(2025, 8, 1, 0, 0),
                end = LocalDateTime.of(2025, 8, 2, 0, 0),
                isAllDay = true,
            ),
        )
    }

    @Test
    fun `formats multi-day all-day event with inclusive end date`() {
        assertEquals(
            "ganztägig, Fr., 1. Aug. – So., 3. Aug.",
            EventTimeFormatter.formatRange(
                start = LocalDateTime.of(2025, 8, 1, 0, 0),
                end = LocalDateTime.of(2025, 8, 4, 0, 0),
                isAllDay = true,
            ),
        )
    }

    @Test
    fun `same-day timed event does not span multiple days`() {
        assertFalse(
            EventTimeFormatter.spansMultipleDays(
                start = LocalDateTime.of(2025, 8, 1, 18, 0),
                end = LocalDateTime.of(2025, 8, 1, 19, 0),
                isAllDay = false,
            ),
        )
    }

    @Test
    fun `multi-day timed event spans multiple days`() {
        assertTrue(
            EventTimeFormatter.spansMultipleDays(
                start = LocalDateTime.of(2025, 8, 1, 18, 0),
                end = LocalDateTime.of(2025, 8, 3, 10, 0),
                isAllDay = false,
            ),
        )
    }

    @Test
    fun `one-day all-day event does not span multiple days with exclusive midnight end`() {
        assertFalse(
            EventTimeFormatter.spansMultipleDays(
                start = LocalDateTime.of(2025, 8, 1, 0, 0),
                end = LocalDateTime.of(2025, 8, 2, 0, 0),
                isAllDay = true,
            ),
        )
    }

    @Test
    fun `multi-day all-day event spans multiple inclusive calendar days`() {
        assertTrue(
            EventTimeFormatter.spansMultipleDays(
                start = LocalDateTime.of(2025, 8, 1, 0, 0),
                end = LocalDateTime.of(2025, 8, 4, 0, 0),
                isAllDay = true,
            ),
        )
    }

    @Test
    fun `all-day event with non-midnight end uses end date as inclusive`() {
        assertTrue(
            EventTimeFormatter.spansMultipleDays(
                start = LocalDateTime.of(2025, 8, 1, 0, 0),
                end = LocalDateTime.of(2025, 8, 2, 12, 0),
                isAllDay = true,
            ),
        )
    }
}
