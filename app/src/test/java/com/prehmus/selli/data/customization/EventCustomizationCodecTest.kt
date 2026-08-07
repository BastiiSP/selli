package com.prehmus.selli.data.customization

import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventCustomizationCodecTest {
    private val codec = EventCustomizationCodec()

    @Test
    fun `round trips occurrence and series customizations`() {
        val customizations = listOf(
            EventCustomization(
                target = CustomizationTarget.Occurrence(
                    EventKey(CalendarSource.GOOGLE_OWN, "event-1"),
                ),
                hidden = false,
                overrides = EventFieldOverrides(
                    title = "Changed \"title\"",
                    date = LocalDate.of(2026, 7, 20),
                    endDate = LocalDate.of(2026, 7, 22),
                    startTime = LocalTime.of(14, 30),
                    endTime = LocalTime.of(16, 0),
                    location = "Berlin\nHbf",
                    description = "Platform 7",
                    category = EventCategory.TOGETHER,
                    blocksSharedFreeTime = true,
                ),
                label = "Original title",
            ),
            EventCustomization(
                target = CustomizationTarget.SeriesFrom(
                    source = CalendarSource.WORK_ICS,
                    seriesId = "series-1",
                    fromStart = LocalDateTime.of(2026, 7, 21, 9, 0),
                ),
                hidden = true,
                overrides = EventFieldOverrides(blocksSharedFreeTime = false),
                label = "Standup",
            ),
        )

        assertEquals(customizations, codec.decode(codec.encode(customizations)))
    }

    @Test
    fun `round trips empty list`() {
        assertEquals(emptyList<EventCustomization>(), codec.decode(codec.encode(emptyList())))
    }

    @Test
    fun `decodes version one customization without free-time field as unspecified`() {
        val legacyJson = """
            {
              "version": 1,
              "customizations": [
                {
                  "targetType": "occurrence",
                  "source": "GOOGLE_OWN",
                  "eventId": "legacy-event",
                  "hidden": false,
                  "label": "Alter Termin"
                }
              ]
            }
        """.trimIndent()

        val customization = codec.decode(legacyJson).single()

        assertNull(customization.overrides.blocksSharedFreeTime)
    }

    @Test
    fun `decodes version one customization without end date as unspecified`() {
        val legacyJson = """
            {
              "version": 1,
              "customizations": [
                {
                  "targetType": "occurrence",
                  "source": "GOOGLE_OWN",
                  "eventId": "legacy-event",
                  "hidden": false,
                  "date": "2026-07-20",
                  "startTime": "14:30",
                  "endTime": "16:00",
                  "label": "Alter Termin"
                }
              ]
            }
        """.trimIndent()

        val customization = codec.decode(legacyJson).single()

        assertNull(customization.overrides.endDate)
    }

    @Test
    fun `decodes version one customization without original category as unspecified`() {
        val legacyJson = """
            {
              "version": 1,
              "customizations": [
                {
                  "targetType": "occurrence",
                  "source": "GOOGLE_OWN",
                  "eventId": "legacy-event",
                  "hidden": false,
                  "label": "Alter Termin"
                }
              ]
            }
        """.trimIndent()

        val customization = codec.decode(legacyJson).single()

        assertNull(customization.originalCategory)
    }

    @Test
    fun `round trip preserves original category`() {
        val customization = EventCustomization(
            target = CustomizationTarget.Occurrence(
                EventKey(CalendarSource.GOOGLE_OWN, "event-1"),
            ),
            hidden = true,
            originalCategory = EventCategory.TOGETHER,
        )

        val decoded = codec.decode(codec.encode(listOf(customization))).single()

        assertEquals(EventCategory.TOGETHER, decoded.originalCategory)
    }
}
