package com.prehmus.selli.data.customization

import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
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
                    startTime = LocalTime.of(14, 30),
                    endTime = LocalTime.of(16, 0),
                    location = "Berlin\nHbf",
                    description = "Platform 7",
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
                label = "Standup",
            ),
        )

        assertEquals(customizations, codec.decode(codec.encode(customizations)))
    }

    @Test
    fun `round trips empty list`() {
        assertEquals(emptyList<EventCustomization>(), codec.decode(codec.encode(emptyList())))
    }
}
