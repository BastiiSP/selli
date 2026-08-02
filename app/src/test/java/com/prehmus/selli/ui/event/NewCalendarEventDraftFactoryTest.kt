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
            startTime = LocalTime.of(18, 0),
            endDay = LocalDate.of(2026, 8, 7),
            endTime = LocalTime.of(19, 0),
            isAllDay = true,
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
                startTime = LocalTime.NOON,
                endDay = LocalDate.of(2026, 8, 6),
                endTime = LocalTime.of(13, 0),
                isAllDay = true,
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
            startTime = LocalTime.of(18, 0),
            endDay = day,
            endTime = LocalTime.of(20, 30),
            isAllDay = false,
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
    fun `trims description and turns blank description into null`() {
        val day = LocalDate.of(2026, 8, 3)

        val withDescription = buildNewCalendarEvent(
            title = "Kino",
            startDay = day,
            startTime = LocalTime.of(18, 0),
            endDay = day,
            endTime = LocalTime.of(20, 30),
            isAllDay = false,
            location = null,
            category = EventCategory.PRIVATE,
            recurrence = null,
            blocksSharedFreeTime = true,
            description = "  Popcorn nicht vergessen  ",
        )
        assertEquals("Popcorn nicht vergessen", withDescription.description)

        val withBlankDescription = buildNewCalendarEvent(
            title = "Kino",
            startDay = day,
            startTime = LocalTime.of(18, 0),
            endDay = day,
            endTime = LocalTime.of(20, 30),
            isAllDay = false,
            location = null,
            category = EventCategory.PRIVATE,
            recurrence = null,
            blocksSharedFreeTime = true,
            description = "   ",
        )
        assertEquals(null, withBlankDescription.description)

        val withoutDescription = buildNewCalendarEvent(
            title = "Kino",
            startDay = day,
            startTime = LocalTime.of(18, 0),
            endDay = day,
            endTime = LocalTime.of(20, 30),
            isAllDay = false,
            location = null,
            category = EventCategory.PRIVATE,
            recurrence = null,
            blocksSharedFreeTime = true,
        )
        assertEquals(null, withoutDescription.description)
    }

    @Test
    fun `builds timed event spanning three calendar days`() {
        val event = buildNewCalendarEvent(
            title = "Wochenendtrip",
            startDay = LocalDate.of(2026, 8, 1),
            startTime = LocalTime.of(18, 0),
            endDay = LocalDate.of(2026, 8, 3),
            endTime = LocalTime.of(10, 0),
            isAllDay = false,
            location = null,
            category = EventCategory.TOGETHER,
            recurrence = null,
            blocksSharedFreeTime = false,
        )

        assertEquals(LocalDateTime.of(2026, 8, 1, 18, 0), event.start)
        assertEquals(LocalDateTime.of(2026, 8, 3, 10, 0), event.end)
    }

    @Test
    fun `rejects timed end date before start date`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            buildNewCalendarEvent(
                title = "Rückwärts",
                startDay = LocalDate.of(2026, 8, 3),
                startTime = LocalTime.of(10, 0),
                endDay = LocalDate.of(2026, 8, 1),
                endTime = LocalTime.of(18, 0),
                isAllDay = false,
                location = null,
                category = EventCategory.PRIVATE,
                recurrence = null,
                blocksSharedFreeTime = false,
            )
        }

        assertEquals("Das Ende muss nach dem Beginn liegen.", exception.message)
    }

    @Test
    fun `rejects timed end not after start on same day`() {
        val day = LocalDate.of(2026, 8, 3)

        listOf(LocalTime.of(10, 0), LocalTime.of(9, 0)).forEach { endTime ->
            assertThrows(IllegalArgumentException::class.java) {
                buildNewCalendarEvent(
                    title = "Ungültig",
                    startDay = day,
                    startTime = LocalTime.of(10, 0),
                    endDay = day,
                    endTime = endTime,
                    isAllDay = false,
                    location = null,
                    category = EventCategory.PRIVATE,
                    recurrence = null,
                    blocksSharedFreeTime = false,
                )
            }
        }
    }

    @Test
    fun `private event does not invite partner`() {
        val day = LocalDate.of(2026, 8, 3)

        val event = buildNewCalendarEvent(
            title = "Sport",
            startDay = day,
            startTime = LocalTime.of(18, 0),
            endDay = day,
            endTime = LocalTime.of(19, 0),
            isAllDay = false,
            location = null,
            category = EventCategory.PRIVATE,
            recurrence = null,
            blocksSharedFreeTime = false,
        )

        assertFalse(event.invitePartner)
    }
}
