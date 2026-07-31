package com.prehmus.selli.data.notification

import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.notification.SharedEventFingerprint
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SharedEventFingerprintCodecTest {
    private val codec = SharedEventFingerprintCodec()

    @Test
    fun `roundtrips multiple fingerprints with special characters`() {
        val fingerprints = linkedMapOf(
            EventKey(CalendarSource.GOOGLE_PARTNER, "id.|=ä") to fingerprint(
                title = "Kino | Essen = schön\nwirklich",
                location = "Frankfurt, Hauptbahnhof | Gleis 1",
                description = "Tickets: A=B | C",
            ),
            EventKey(CalendarSource.WORK_ICS, "work/42") to fingerprint(
                title = "Feierabend & Wir-Zeit",
                location = "Bei Melli",
                description = "",
            ),
        )

        assertEquals(fingerprints, codec.decode(codec.encode(fingerprints)))
    }

    @Test
    fun `preserves null location and description`() {
        val fingerprints = mapOf(
            EventKey(CalendarSource.GOOGLE_PARTNER, "nullable") to fingerprint(
                title = "Ohne Details",
                location = null,
                description = null,
            ),
        )

        val decoded = codec.decode(codec.encode(fingerprints))

        assertEquals(fingerprints, decoded)
        assertEquals(null, decoded.values.single().location)
        assertEquals(null, decoded.values.single().description)
    }

    @Test
    fun `roundtrips an empty map`() {
        assertEquals(emptyMap<EventKey, SharedEventFingerprint>(), codec.decode(codec.encode(emptyMap())))
    }

    @Test
    fun `skips an incomplete entry while keeping intact entries`() {
        val intactKey = EventKey(CalendarSource.GOOGLE_PARTNER, "intact")
        val brokenKey = EventKey(CalendarSource.GOOGLE_PARTNER, "broken")
        val encoded = codec.encode(
            linkedMapOf(
                intactKey to fingerprint(title = "Intakt"),
                brokenKey to fingerprint(title = "Kaputt"),
            ),
        ).toMutableMap()
        val brokenIdEntry = encoded.entries.single { (key, value) ->
            key.endsWith(".event_id") && value == "broken"
        }
        val brokenPrefix = brokenIdEntry.key.removeSuffix("event_id")
        encoded.remove("${brokenPrefix}title")

        val decoded = codec.decode(encoded)

        assertEquals(mapOf(intactKey to fingerprint(title = "Intakt")), decoded)
        assertFalse(decoded.containsKey(brokenKey))
    }

    private fun fingerprint(
        title: String,
        location: String? = "Berlin",
        description: String? = "Beschreibung",
    ): SharedEventFingerprint =
        SharedEventFingerprint(
            title = title,
            start = LocalDateTime.of(2026, 8, 8, 10, 0),
            end = LocalDateTime.of(2026, 8, 8, 12, 0),
            isAllDay = false,
            location = location,
            description = description,
        )
}
