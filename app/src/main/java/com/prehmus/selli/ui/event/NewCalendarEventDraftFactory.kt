package com.prehmus.selli.ui.event

import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventRecurrence
import com.prehmus.selli.domain.model.NewCalendarEvent
import java.time.LocalDate
import java.time.LocalTime

/**
 * Übersetzt Formulardaten in Sellis/Googles Endzeitpunkt.
 * Bei ganztägigen Terminen ist der gewählte Endtag inklusiv.
 * Die Funktion ist UI-frameworkfrei, damit die Datumsgrenzen direkt testbar bleiben.
 */
internal fun buildNewCalendarEvent(
    title: String,
    startDay: LocalDate,
    startTime: LocalTime,
    endDay: LocalDate,
    endTime: LocalTime,
    isAllDay: Boolean,
    location: String?,
    category: EventCategory,
    recurrence: EventRecurrence?,
    blocksSharedFreeTime: Boolean,
    description: String? = null,
): NewCalendarEvent {
    val normalizedTitle = title.trim()
    require(normalizedTitle.isNotEmpty()) { "Der Titel darf nicht leer sein." }

    val start = if (isAllDay) {
        require(!endDay.isBefore(startDay)) {
            "Der Endtag darf nicht vor dem Starttag liegen."
        }
        startDay.atStartOfDay()
    } else {
        startDay.atTime(startTime)
    }
    val end = if (isAllDay) {
        endDay.plusDays(1).atStartOfDay()
    } else {
        endDay.atTime(endTime)
    }
    if (!isAllDay) {
        require(end.isAfter(start)) { "Das Ende muss nach dem Beginn liegen." }
    }

    return NewCalendarEvent(
        title = normalizedTitle,
        start = start,
        end = end,
        isAllDay = isAllDay,
        location = location?.trim()?.ifBlank { null },
        description = description?.trim()?.ifBlank { null },
        invitePartner = category == EventCategory.TOGETHER,
        recurrence = recurrence,
        blocksSharedFreeTime = isAllDay && blocksSharedFreeTime,
    )
}
