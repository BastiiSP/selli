package com.prehmus.selli.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class EventKey(val source: CalendarSource, val eventId: String)

/** Alle Felder optional; null = Originalwert behalten. */
data class EventFieldOverrides(
    val title: String? = null,
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String? = null,
    val description: String? = null,
    val category: EventCategory? = null,
) {
    fun isEmpty(): Boolean =
        title == null &&
            date == null &&
            startTime == null &&
            endTime == null &&
            location == null &&
            description == null &&
            category == null
}

sealed interface CustomizationTarget {
    data class Occurrence(val key: EventKey) : CustomizationTarget

    /**
     * Dieses und alle folgenden Vorkommen der Serie (occurrence.start >= fromStart).
     * Frühere Vorkommen bleiben unberührt.
     */
    data class SeriesFrom(
        val source: CalendarSource,
        val seriesId: String,
        val fromStart: LocalDateTime,
    ) : CustomizationTarget
}

data class EventCustomization(
    val target: CustomizationTarget,
    val hidden: Boolean,
    val overrides: EventFieldOverrides = EventFieldOverrides(),
    val label: String = "",
)
