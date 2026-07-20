package com.prehmus.selli.data.ics

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCalendarParserTest {
    private val range = DateRange(
        start = LocalDate.of(2026, 1, 1),
        endInclusive = LocalDate.of(2026, 1, 31),
    )

    @Test
    fun `parses all-day event with implicit next-day end`() {
        val events = parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:all-day-1
            DTSTART;VALUE=DATE:20260105
            SUMMARY:Office closed
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        assertEquals(1, events.size)
        assertEquals("all-day-1", events.single().id)
        assertEquals("Office closed", events.single().title)
        assertEquals(LocalDateTime.of(2026, 1, 5, 0, 0), events.single().start)
        assertEquals(LocalDateTime.of(2026, 1, 6, 0, 0), events.single().end)
        assertTrue(events.single().isAllDay)
    }

    @Test
    fun `parses local time with TZID and utc Z suffix into system zone`() {
        val zone = ZoneId.of("Europe/Berlin")
        val events = IcsCalendarParser(systemZone = zone).parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:tzid-1
            DTSTART;TZID=Europe/Berlin:20260106T090000
            DTEND;TZID=Europe/Berlin:20260106T100000
            SUMMARY:Berlin start
            END:VEVENT
            BEGIN:VEVENT
            UID:utc-1
            DTSTART:20260106T080000Z
            DTEND:20260106T090000Z
            SUMMARY:UTC start
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            range,
        ).sortedBy { it.id }

        assertEquals(2, events.size)
        assertEquals(LocalDateTime.of(2026, 1, 6, 9, 0), events[0].start)
        assertEquals(LocalDateTime.of(2026, 1, 6, 9, 0), events[1].start)
    }

    @Test
    fun `unfolds lines and unescapes text values`() {
        val events = parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:text-1
            DTSTART:20260107T120000
            DTEND:20260107T130000
            SUMMARY:Long planning
              session
            LOCATION:Room A\, floor 2\; west wing
            DESCRIPTION:Line one\nLine two\\done
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        val event = events.single()
        assertEquals("Long planning session", event.title)
        assertEquals("Room A, floor 2; west wing", event.location)
        assertEquals("Line one\nLine two\\done", event.description)
    }

    @Test
    fun `expands weekly recurrence with byday interval until count and exdate`() {
        val events = parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:weekly-1
            DTSTART;TZID=Europe/Berlin:20260105T090000
            DTEND;TZID=Europe/Berlin:20260105T100000
            SUMMARY:Standup
            RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=6;BYDAY=MO,WE;UNTIL=20260121T235959Z
            EXDATE;TZID=Europe/Berlin:20260107T090000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 5, 9, 0),
                LocalDateTime.of(2026, 1, 12, 9, 0),
                LocalDateTime.of(2026, 1, 14, 9, 0),
                LocalDateTime.of(2026, 1, 19, 9, 0),
                LocalDateTime.of(2026, 1, 21, 9, 0),
            ),
            events.map { it.start },
        )
        assertEquals(
            listOf(
                "weekly-1-20260105T090000",
                "weekly-1-20260112T090000",
                "weekly-1-20260114T090000",
                "weekly-1-20260119T090000",
                "weekly-1-20260121T090000",
            ),
            events.map { it.id },
        )
        assertEquals(5, events.map { it.id }.distinct().size)
        assertEquals(setOf("weekly-1"), events.map { it.seriesId }.toSet())
    }

    @Test
    fun `expands daily recurrence with byday filter`() {
        val events = parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:daily-1
            DTSTART;TZID=Europe/Berlin:20260105T090000
            DTEND;TZID=Europe/Berlin:20260105T100000
            SUMMARY:Weekday daily
            RRULE:FREQ=DAILY;COUNT=3;BYDAY=MO,WE,FR
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 5, 9, 0),
                LocalDateTime.of(2026, 1, 7, 9, 0),
                LocalDateTime.of(2026, 1, 9, 9, 0),
            ),
            events.map { it.start },
        )
    }

    @Test
    fun `skips cancelled events and filters events outside requested range`() {
        val events = parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:cancelled-1
            DTSTART:20260108T090000
            DTEND:20260108T100000
            SUMMARY:Cancelled
            STATUS:CANCELLED
            END:VEVENT
            BEGIN:VEVENT
            UID:outside-1
            DTSTART:20260208T090000
            DTEND:20260208T100000
            SUMMARY:Outside
            END:VEVENT
            BEGIN:VEVENT
            UID:inside-1
            DTSTART:20260109T090000
            DTEND:20260109T100000
            SUMMARY:Inside
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        assertEquals(listOf("inside-1"), events.map { it.id })
    }

    @Test
    fun `maps work calendar metadata`() {
        val event = parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:metadata-1
            DTSTART:20260110T090000
            DTEND:20260110T100000
            SUMMARY:Work
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        ).single()

        assertEquals(CalendarSource.WORK_ICS, event.source)
        assertEquals(Person.BASTI, event.owner)
        assertFalse(event.isSharedEvent)
        assertEquals("metadata-1", event.seriesId)
    }

    @Test
    fun `maps configured owner while keeping work source`() {
        val event = IcsCalendarParser(
            systemZone = ZoneId.of("Europe/Berlin"),
            owner = Person.MELLI,
        ).parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:melli-work-1
            DTSTART:20260110T090000
            DTEND:20260110T100000
            SUMMARY:Work
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            range,
        ).single()

        assertEquals(Person.MELLI, event.owner)
        assertEquals(CalendarSource.WORK_ICS, event.source)
        assertFalse(event.isSharedEvent)
    }

    private fun parse(ics: String): List<CalendarEvent> {
        return IcsCalendarParser(systemZone = ZoneId.of("Europe/Berlin")).parse(ics, range)
    }
}
