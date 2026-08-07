package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SharedEventNotificationFormatterTest {
    private val formatter = SharedEventNotificationFormatter()

    @Test
    fun `formats a new event notification`() {
        val content = formatter.format(
            SharedEventChange.New(event()),
            partnerDisplayName = "Melli",
        )

        assertEquals("Melli hat gemeinsame Zeit eingetragen", content.title)
        assertEquals("Wochenende bei euch – Sa., 8. August", content.text)
    }

    @Test
    fun `formats an updated event notification with all changed fields`() {
        val content = formatter.format(
            SharedEventChange.Updated(
                event = event(),
                changedFields = listOf(
                    "Neue Zeit: 10:00–12:00",
                    "Neuer Ort: Frankfurt",
                ),
            ),
            partnerDisplayName = "Melli",
        )

        assertEquals(
            "Melli hat euren gemeinsamen Termin aktualisiert",
            content.title,
        )
        assertEquals(
            "Wochenende bei euch – Neue Zeit: 10:00–12:00, Neuer Ort: Frankfurt",
            content.text,
        )
    }

    @Test
    fun `formats a delete request notification`() {
        val content = formatter.format(
            SharedEventChange.DeleteRequested(
                event = event(),
                requestedBy = Person.MELLI,
            ),
            partnerDisplayName = "Melli",
        )

        assertEquals("Melli möchte einen Termin löschen", content.title)
        assertEquals(
            "Wochenende bei euch – öffnen und löschen, um die Anfrage abzuschließen",
            content.text,
        )
    }

    @Test
    fun `uses the same notification id for the same event key`() {
        val first = formatter.format(SharedEventChange.New(event(title = "Erster Titel")), "Melli")
        val second = formatter.format(
            SharedEventChange.Updated(
                event = event(title = "Anderer Titel"),
                changedFields = listOf("Neuer Titel: Anderer Titel"),
            ),
            "Melli",
        )

        assertEquals(first.notificationId, second.notificationId)
    }

    @Test
    fun `uses different notification ids for different event keys`() {
        val first = formatter.format(SharedEventChange.New(event(id = "event-1")), "Melli")
        val second = formatter.format(SharedEventChange.New(event(id = "event-2")), "Melli")

        assertNotEquals(first.notificationId, second.notificationId)
    }

    private fun event(
        id: String = "event-1",
        title: String = "Wochenende bei euch",
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = LocalDateTime.of(2026, 8, 8, 10, 0),
            end = LocalDateTime.of(2026, 8, 8, 12, 0),
            isAllDay = false,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = Person.MELLI,
            isSharedEvent = true,
            category = EventCategory.TOGETHER,
        )
}
