package com.prehmus.selli.data.google

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Event.ExtendedProperties
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarEventMapperTest {
    private val mapper = GoogleCalendarEventMapper(zoneId = ZoneId.of("Europe/Berlin"))

    @Test
    fun `maps timed google event into requested time zone`() {
        val googleEvent = Event()
            .setId("google-1")
            .setSummary("Train")
            .setLocation("Station")
            .setDescription("Platform 7")
            .setStart(
                EventDateTime()
                    .setDateTime(DateTime("2026-07-18T10:30:00-04:00"))
                    .setTimeZone("America/New_York"),
            )
            .setEnd(
                EventDateTime()
                    .setDateTime(DateTime("2026-07-18T12:00:00-04:00"))
                    .setTimeZone("America/New_York"),
            )

        val mapped = mapper.toCalendarEvent(
            event = googleEvent,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            ownEmail = "basti@example.test",
            partnerEmail = "melli@example.test",
        )

        assertEquals("google-1", mapped.id)
        assertEquals("Train", mapped.title)
        assertEquals(LocalDateTime.of(2026, 7, 18, 16, 30), mapped.start)
        assertEquals(LocalDateTime.of(2026, 7, 18, 18, 0), mapped.end)
        assertFalse(mapped.isAllDay)
        assertEquals(CalendarSource.GOOGLE_OWN, mapped.source)
        assertEquals(Person.BASTI, mapped.owner)
        assertEquals("Station", mapped.location)
        assertEquals("Platform 7", mapped.description)
    }

    @Test
    fun `maps all day google event from date fields`() {
        val googleEvent = Event()
            .setId("google-2")
            .setSummary("Holiday")
            .setStart(EventDateTime().setDate(DateTime("2026-07-18")))
            .setEnd(EventDateTime().setDate(DateTime("2026-07-19")))

        val mapped = mapper.toCalendarEvent(
            event = googleEvent,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = Person.MELLI,
            ownEmail = "basti@example.test",
            partnerEmail = "melli@example.test",
        )

        assertEquals(LocalDateTime.of(2026, 7, 18, 0, 0), mapped.start)
        assertEquals(LocalDateTime.of(2026, 7, 19, 0, 0), mapped.end)
        assertTrue(mapped.isAllDay)
        assertEquals(CalendarSource.GOOGLE_PARTNER, mapped.source)
        assertEquals(Person.MELLI, mapped.owner)
    }

    @Test
    fun `maps recurring event id as series id`() {
        val mapped = mapper.toCalendarEvent(
            event = timedEvent().setRecurringEventId("series-1"),
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            ownEmail = "basti@example.test",
            partnerEmail = "melli@example.test",
        )

        assertEquals("series-1", mapped.seriesId)
    }

    @Test
    fun `detects shared event when both people are attendees`() {
        val googleEvent = timedEvent()
            .setAttendees(
                listOf(
                    EventAttendee().setEmail("BASTI@example.test"),
                    EventAttendee().setEmail("melli@example.test"),
                ),
            )

        val mapped = mapper.toCalendarEvent(
            event = googleEvent,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            ownEmail = "basti@example.test",
            partnerEmail = "melli@example.test",
        )

        assertTrue(mapped.isSharedEvent)
    }

    @Test
    fun `detects shared event from selli extended property`() {
        val googleEvent = timedEvent()
            .setExtendedProperties(
                ExtendedProperties()
                    .setShared(mapOf("selli:shared" to "true")),
            )

        val mapped = mapper.toCalendarEvent(
            event = googleEvent,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = Person.MELLI,
            ownEmail = "basti@example.test",
            partnerEmail = "melli@example.test",
        )

        assertTrue(mapped.isSharedEvent)
    }

    private fun timedEvent(): Event =
        Event()
            .setId("shared")
            .setSummary("Shared")
            .setStart(EventDateTime().setDateTime(DateTime("2026-07-18T10:00:00+02:00")))
            .setEnd(EventDateTime().setDateTime(DateTime("2026-07-18T11:00:00+02:00")))
}
