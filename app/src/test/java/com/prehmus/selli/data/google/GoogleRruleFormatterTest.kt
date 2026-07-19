package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.EventRecurrence
import com.prehmus.selli.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleRruleFormatterTest {
    private val formatter = GoogleRruleFormatter(ZoneId.of("Europe/Berlin"))

    @Test
    fun `formats every supported frequency without an end date`() {
        val expected = mapOf(
            RecurrenceFrequency.DAILY to "RRULE:FREQ=DAILY",
            RecurrenceFrequency.WEEKLY to "RRULE:FREQ=WEEKLY",
            RecurrenceFrequency.MONTHLY to "RRULE:FREQ=MONTHLY",
            RecurrenceFrequency.YEARLY to "RRULE:FREQ=YEARLY",
        )

        expected.forEach { (frequency, rrule) ->
            assertEquals(
                rrule,
                formatter.format(EventRecurrence(frequency = frequency), isAllDay = false),
            )
        }
    }

    @Test
    fun `formats all-day until as an inclusive date`() {
        val recurrence = EventRecurrence(
            frequency = RecurrenceFrequency.WEEKLY,
            until = LocalDate.of(2026, 10, 5),
        )

        assertEquals(
            "RRULE:FREQ=WEEKLY;UNTIL=20261005",
            formatter.format(recurrence, isAllDay = true),
        )
    }

    @Test
    fun `converts end of summer day from user zone to UTC for timed events`() {
        val recurrence = EventRecurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            until = LocalDate.of(2026, 7, 15),
        )

        assertEquals(
            "RRULE:FREQ=MONTHLY;UNTIL=20260715T215959Z",
            formatter.format(recurrence, isAllDay = false),
        )
    }

    @Test
    fun `converts end of winter day from user zone to UTC for timed events`() {
        val recurrence = EventRecurrence(
            frequency = RecurrenceFrequency.YEARLY,
            until = LocalDate.of(2026, 1, 15),
        )

        assertEquals(
            "RRULE:FREQ=YEARLY;UNTIL=20260115T225959Z",
            formatter.format(recurrence, isAllDay = false),
        )
    }
}
