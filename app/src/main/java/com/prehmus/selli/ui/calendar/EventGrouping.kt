package com.prehmus.selli.ui.calendar

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory

sealed interface EventListRow {
    data class Single(val event: CalendarEvent) : EventListRow

    data class Group(
        val wirTermin: CalendarEvent,
        val overlapping: List<CalendarEvent>,
    ) : EventListRow
}

/**
 * Gruppiert die Termine EINES Tages: jeder Wir-Termin (category == TOGETHER) zieht alle Termine
 * an sich, deren Zeitspanne sich mit seiner überschneidet.
 * Eingabe: die Termine eines Tages in der Reihenfolge, die `groupByDay()` in CalendarViewModel.kt
 * liefert (Ganztägige zuerst, dann nach Start).
 */
fun List<CalendarEvent>.toEventListRows(): List<EventListRow> {
    val togetherIndices = indices.filter { this[it].category == EventCategory.TOGETHER }
    if (togetherIndices.isEmpty()) {
        return map(EventListRow::Single)
    }

    val consumed = mutableSetOf<Int>()
    val rows = mutableListOf<EventListRow>()
    val sortedTogetherIndices = togetherIndices.sortedWith(
        compareBy<Int> { this[it].start }
            .thenBy { this[it].title }
            .thenBy { this[it].id },
    )

    for (wirTerminIndex in sortedTogetherIndices) {
        if (wirTerminIndex in consumed) continue

        val wirTermin = this[wirTerminIndex]
        val overlappingIndices = indices.filter { eventIndex ->
            eventIndex != wirTerminIndex &&
                eventIndex !in consumed &&
                wirTermin.overlaps(this[eventIndex])
        }
        consumed += wirTerminIndex
        consumed += overlappingIndices

        rows += if (overlappingIndices.isEmpty()) {
            EventListRow.Single(wirTermin)
        } else {
            EventListRow.Group(
                wirTermin = wirTermin,
                overlapping = overlappingIndices
                    .map(::get)
                    .sortedWith(eventOrder),
            )
        }
    }

    indices
        .filterNot(consumed::contains)
        .mapTo(rows) { EventListRow.Single(this[it]) }

    return rows.sortedWith(compareBy(eventOrder) { it.anchor })
}

private val eventOrder: Comparator<CalendarEvent> =
    compareBy<CalendarEvent> { !it.isAllDay }
        .thenBy { it.start }
        .thenBy { it.title }
        .thenBy { it.id }

private val EventListRow.anchor: CalendarEvent
    get() = when (this) {
        is EventListRow.Group -> wirTermin
        is EventListRow.Single -> event
    }

private fun CalendarEvent.overlaps(other: CalendarEvent): Boolean =
    start < other.end && end > other.start
