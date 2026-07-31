package com.prehmus.selli.data.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.FreeSlot
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotCodecTest {
    private val codec = WidgetSnapshotCodec()

    @Test
    fun `roundtrips snapshot with next event`() {
        val snapshot = WidgetSnapshot(
            partnerPerson = Person.MELLI,
            partnerDisplayName = "Melli",
            nextEvent = CalendarEvent(
                id = "event-1",
                title = "Dinner",
                start = LocalDateTime.of(2026, 7, 19, 19, 0),
                end = LocalDateTime.of(2026, 7, 19, 21, 0),
                isAllDay = false,
                source = CalendarSource.GOOGLE_PARTNER,
                owner = Person.MELLI,
                isSharedEvent = true,
                location = "Berlin",
                description = "Bring flowers",
            ),
            updatedAt = LocalDateTime.of(2026, 7, 19, 12, 15),
        )

        assertEquals(snapshot, codec.decode(codec.encode(snapshot)))
    }

    @Test
    fun `roundtrips snapshot without next event`() {
        val snapshot = WidgetSnapshot(
            partnerPerson = Person.MELLI,
            partnerDisplayName = "Melli",
            nextEvent = null,
            updatedAt = LocalDateTime.of(2026, 7, 19, 12, 15),
        )

        assertEquals(snapshot, codec.decode(codec.encode(snapshot)))
    }

    @Test
    fun `roundtrips snapshot with shared event and free slot`() {
        val snapshot = WidgetSnapshot(
            partnerPerson = Person.MELLI,
            partnerDisplayName = "Melli",
            nextEvent = CalendarEvent(
                id = "work-1",
                title = "Workshop",
                start = LocalDateTime.of(2026, 8, 3, 9, 0),
                end = LocalDateTime.of(2026, 8, 3, 17, 0),
                isAllDay = false,
                source = CalendarSource.WORK_ICS,
                owner = Person.BASTI,
                isSharedEvent = false,
                category = EventCategory.WORK,
            ),
            updatedAt = LocalDateTime.of(2026, 7, 31, 12, 15),
            nextSharedEvent = CalendarEvent(
                id = "together-1",
                title = "Ausflug",
                start = LocalDateTime.of(2026, 8, 5, 10, 0),
                end = LocalDateTime.of(2026, 8, 5, 18, 0),
                isAllDay = false,
                source = CalendarSource.GOOGLE_OWN,
                owner = Person.BASTI,
                isSharedEvent = true,
                location = "Potsdam",
                description = "Picknick einpacken",
                category = EventCategory.TOGETHER,
            ),
            nextFreeSlot = FreeSlot(
                day = LocalDate.of(2026, 8, 6),
                block = FreeTimeBlock(
                    start = LocalDateTime.of(2026, 8, 6, 14, 0),
                    end = LocalDateTime.of(2026, 8, 6, 18, 0),
                ),
            ),
        )

        assertEquals(snapshot, codec.decode(codec.encode(snapshot)))
    }

    @Test
    fun `roundtrips snapshot with null shared event and free slot`() {
        val snapshot = WidgetSnapshot(
            partnerPerson = Person.MELLI,
            partnerDisplayName = "Melli",
            nextEvent = null,
            updatedAt = LocalDateTime.of(2026, 7, 31, 12, 15),
            nextSharedEvent = null,
            nextFreeSlot = null,
        )

        assertEquals(snapshot, codec.decode(codec.encode(snapshot)))
    }

    @Test
    fun `decodes legacy snapshot without shared event and free slot keys`() {
        val legacyValues = mapOf(
            "partner_person" to "MELLI",
            "partner_display_name" to "Melli",
            "updated_at" to "2026-07-31T12:15",
            "next_event_present" to "true",
            "next_event_id" to "event-1",
            "next_event_title" to "Dinner",
            "next_event_start" to "2026-07-31T19:00",
            "next_event_end" to "2026-07-31T21:00",
            "next_event_is_all_day" to "false",
            "next_event_source" to "GOOGLE_PARTNER",
            "next_event_owner" to "MELLI",
            "next_event_is_shared" to "true",
            "next_event_location" to "Berlin",
            "next_event_description" to "Bring flowers",
        )
        val expectedNextEvent = CalendarEvent(
            id = "event-1",
            title = "Dinner",
            start = LocalDateTime.of(2026, 7, 31, 19, 0),
            end = LocalDateTime.of(2026, 7, 31, 21, 0),
            isAllDay = false,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = Person.MELLI,
            isSharedEvent = true,
            location = "Berlin",
            description = "Bring flowers",
            category = EventCategory.PRIVATE,
        )

        val decoded = codec.decode(legacyValues)

        assertEquals(Person.MELLI, decoded?.partnerPerson)
        assertEquals("Melli", decoded?.partnerDisplayName)
        assertEquals(expectedNextEvent, decoded?.nextEvent)
        assertEquals(LocalDateTime.of(2026, 7, 31, 12, 15), decoded?.updatedAt)
        assertNull(decoded?.nextSharedEvent)
        assertNull(decoded?.nextFreeSlot)
    }

    @Test
    fun `returns null for incomplete data`() {
        val encoded = codec.encode(
            WidgetSnapshot(
                partnerPerson = Person.MELLI,
                partnerDisplayName = "Melli",
                nextEvent = null,
                updatedAt = LocalDateTime.of(2026, 7, 19, 12, 15),
            ),
        ) - "updated_at"

        assertNull(codec.decode(encoded))
    }

    @Test
    fun `returns null for broken enum data`() {
        val encoded = codec.encode(
            WidgetSnapshot(
                partnerPerson = Person.MELLI,
                partnerDisplayName = "Melli",
                nextEvent = null,
                updatedAt = LocalDateTime.of(2026, 7, 19, 12, 15),
            ),
        ) + ("partner_person" to "UNKNOWN")

        assertNull(codec.decode(encoded))
    }
}
