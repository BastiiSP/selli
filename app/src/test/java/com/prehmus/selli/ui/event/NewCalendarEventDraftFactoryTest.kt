package com.prehmus.selli.ui.event

import com.prehmus.selli.domain.model.EventCategory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCalendarEventDraftFactoryTest {
    @Test
    fun `builds five-day all-day event with exclusive end after inclusive selection`() {
        val event = buildNewCalendarEvent(
            title = " Urlaub ",
            startDay = LocalDate.of(2026, 8, 3),
            endDayInclusive = LocalDate.of(2026, 8, 7),
            isAllDay = true,
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(19, 0),
            location = " Ostsee ",
            category = EventCategory.TOGETHER,
            recurrence = null,
            blocksSharedFreeTime = true,
        )

        assertEquals("Urlaub", event.title)
        assertEquals("Ostsee", event.location)
        assertEquals(LocalDateTime.of(2026, 8, 3, 0, 0), event.start)
        assertEquals(LocalDateTime.of(2026, 8, 8, 0, 0), event.end)
        assertTrue(event.isAllDay)
        assertTrue(event.blocksSharedFreeTime)
        assertTrue(event.invitePartner)
    }

    @Test
    fun `rejects all-day end before start`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildNewCalendarEvent(
                title = "Ausflug",
                startDay = LocalDate.of(2026, 8, 7),
                endDayInclusive = LocalDate.of(2026, 8, 6),
                isAllDay = true,
                startTime = LocalTime.NOON,
                endTime = LocalTime.of(13, 0),
                location = null,
                category = EventCategory.PRIVATE,
                recurrence = null,
                blocksSharedFreeTime = false,
            )
        }
    }

    @Test
    fun `builds timed event on start day without all-day free-time setting`() {
        val day = LocalDate.of(2026, 8, 3)

        val event = buildNewCalendarEvent(
            title = "Kino",
            startDay = day,
            endDayInclusive = day.plusDays(4),
            isAllDay = false,
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(20, 30),
            location = "  ",
            category = EventCategory.WORK,
            recurrence = null,
            blocksSharedFreeTime = true,
        )

        assertEquals(day.atTime(18, 0), event.start)
        assertEquals(day.atTime(20, 30), event.end)
        assertFalse(event.isAllDay)
        assertFalse(event.blocksSharedFreeTime)
        assertFalse(event.invitePartner)
        assertEquals(null, event.location)
    }

    @Test
    fun `private event does not invite partner`() {
        val day = LocalDate.of(2026, 8, 3)

        val event = buildNewCalendarEvent(
            title = "Sport",
            startDay = day,
            endDayInclusive = day,
            isAllDay = false,
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(19, 0),
            location = null,
            category = EventCategory.PRIVATE,
            recurrence = null,
            blocksSharedFreeTime = false,
        )

        assertFalse(event.invitePartner)
    }
}
