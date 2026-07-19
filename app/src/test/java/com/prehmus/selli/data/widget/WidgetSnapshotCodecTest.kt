package com.prehmus.selli.data.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
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
