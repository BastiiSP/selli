package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerSharedEventChangeDetectorTest {
    private val detector = PartnerSharedEventChangeDetector()

    @Test
    fun `reports a new partner together event`() {
        val event = event(id = "new")

        val result = detector.detect(listOf(event), Person.MELLI, emptyMap())

        assertEquals(listOf(SharedEventChange.New(event)), result.changes)
        assertEquals(mapOf(key("new") to event.fingerprint()), result.updatedFingerprints)
    }

    @Test
    fun `ignores own together event completely`() {
        val ownEvent = event(id = "own", owner = Person.BASTI)

        val result = detector.detect(listOf(ownEvent), Person.MELLI, emptyMap())

        assertTrue(result.changes.isEmpty())
        assertTrue(result.updatedFingerprints.isEmpty())
    }

    @Test
    fun `ignores partner events outside together category`() {
        val privateEvent = event(id = "private", category = EventCategory.PRIVATE)
        val workEvent = event(id = "work", category = EventCategory.WORK)

        val result = detector.detect(
            listOf(privateEvent, workEvent),
            Person.MELLI,
            emptyMap(),
        )

        assertTrue(result.changes.isEmpty())
        assertTrue(result.updatedFingerprints.isEmpty())
    }

    @Test
    fun `reports a moved known event with a time change`() {
        val previous = event(id = "moved")
        val moved = previous.copy(
            start = previous.start.plusHours(2),
            end = previous.end.plusHours(2),
        )

        val result = detector.detect(
            listOf(moved),
            Person.MELLI,
            mapOf(key("moved") to previous.fingerprint()),
        )

        val change = result.changes.single() as SharedEventChange.Updated
        assertEquals(moved, change.event)
        assertEquals(1, change.changedFields.size)
        assertTrue(change.changedFields.single().startsWith("Neue Zeit:"))
    }

    @Test
    fun `reports a new location on a known event`() {
        val previous = event(id = "location", location = "Berlin")
        val moved = previous.copy(location = "Frankfurt")

        val result = detector.detect(
            listOf(moved),
            Person.MELLI,
            mapOf(key("location") to previous.fingerprint()),
        )

        val change = result.changes.single() as SharedEventChange.Updated
        assertEquals(listOf("Neuer Ort: Frankfurt"), change.changedFields)
    }

    @Test
    fun `reports a new title on a known event`() {
        val previous = event(id = "title", title = "Alter Titel")
        val renamed = previous.copy(title = "Neuer Titel")

        val result = detector.detect(
            listOf(renamed),
            Person.MELLI,
            mapOf(key("title") to previous.fingerprint()),
        )

        val change = result.changes.single() as SharedEventChange.Updated
        assertEquals(listOf("Neuer Titel: Neuer Titel"), change.changedFields)
    }

    @Test
    fun `reports an updated description without exposing its text`() {
        val previous = event(id = "description", description = "Alt")
        val updated = previous.copy(description = "Vertraulicher neuer Text")

        val result = detector.detect(
            listOf(updated),
            Person.MELLI,
            mapOf(key("description") to previous.fingerprint()),
        )

        val change = result.changes.single() as SharedEventChange.Updated
        assertEquals(listOf("Beschreibung aktualisiert"), change.changedFields)
    }

    @Test
    fun `keeps an unchanged event in fingerprints without reporting it`() {
        val event = event(id = "unchanged")

        val result = detector.detect(
            listOf(event),
            Person.MELLI,
            mapOf(key("unchanged") to event.fingerprint()),
        )

        assertTrue(result.changes.isEmpty())
        assertEquals(mapOf(key("unchanged") to event.fingerprint()), result.updatedFingerprints)
    }

    @Test
    fun `reports every partner together event on the first run`() {
        val events = listOf(
            event(id = "third", title = "C", start = START.plusDays(2)),
            event(id = "first", title = "A"),
            event(id = "second", title = "B", start = START.plusDays(1)),
        )

        val result = detector.detect(events, Person.MELLI, emptyMap())

        assertEquals(
            listOf("first", "second", "third"),
            result.changes.map { it.event.id },
        )
        assertTrue(result.changes.all { it is SharedEventChange.New })
    }

    @Test
    fun `fingerprints always replace stale values with the current state`() {
        val current = event(id = "current", title = "Aktuell")

        val result = detector.detect(
            listOf(current),
            Person.MELLI,
            mapOf(
                key("current") to current.fingerprint(),
                key("removed") to event(id = "removed").fingerprint(),
            ),
        )

        assertTrue(result.changes.isEmpty())
        assertEquals(mapOf(key("current") to current.fingerprint()), result.updatedFingerprints)
    }

    @Test
    fun `orders multiple changed fields as title time location description`() {
        val previous = event(
            id = "many",
            title = "Alt",
            location = "Berlin",
            description = "Alt",
        )
        val updated = previous.copy(
            title = "Neu",
            start = previous.start.plusHours(1),
            end = previous.end.plusHours(1),
            location = null,
            description = "Neu",
        )

        val result = detector.detect(
            listOf(updated),
            Person.MELLI,
            mapOf(key("many") to previous.fingerprint()),
        )

        val change = result.changes.single() as SharedEventChange.Updated
        assertEquals(
            listOf(
                "Neuer Titel: Neu",
                "Neue Zeit: 11:00–13:00",
                "Ort entfernt",
                "Beschreibung aktualisiert",
            ),
            change.changedFields,
        )
    }

    private fun event(
        id: String,
        title: String = "Wochenende",
        start: LocalDateTime = START,
        end: LocalDateTime = START.plusHours(2),
        isAllDay: Boolean = false,
        source: CalendarSource = CalendarSource.GOOGLE_PARTNER,
        owner: Person = Person.MELLI,
        location: String? = null,
        description: String? = null,
        category: EventCategory = EventCategory.TOGETHER,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = start,
            end = end,
            isAllDay = isAllDay,
            source = source,
            owner = owner,
            isSharedEvent = true,
            location = location,
            description = description,
            category = category,
        )

    private fun key(
        eventId: String,
        source: CalendarSource = CalendarSource.GOOGLE_PARTNER,
    ): EventKey = EventKey(source = source, eventId = eventId)

    private companion object {
        val START: LocalDateTime = LocalDateTime.of(2026, 8, 8, 10, 0)
    }
}
