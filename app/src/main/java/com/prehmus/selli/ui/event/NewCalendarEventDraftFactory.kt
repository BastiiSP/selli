package com.prehmus.selli.ui.event

import com.prehmus.selli.domain.model.EventRecurrence
import com.prehmus.selli.domain.model.NewCalendarEvent
import java.time.LocalDate
import java.time.LocalTime

/**
 * Übersetzt die inklusiven Formulardaten in Sellis/Googles exklusiven Endzeitpunkt.
 * Die Funktion ist UI-frameworkfrei, damit die Datumsgrenzen direkt testbar bleiben.
 */
internal fun buildNewCalendarEvent(
    title: String,
    startDay: LocalDate,
    endDayInclusive: LocalDate,
    isAllDay: Boolean,
    startTime: LocalTime,
    endTime: LocalTime,
    location: String?,
    invitePartner: Boolean,
    recurrence: EventRecurrence?,
    blocksSharedFreeTime: Boolean,
): NewCalendarEvent {
    val normalizedTitle = title.trim()
    require(normalizedTitle.isNotEmpty()) { "Der Titel darf nicht leer sein." }

    val start = if (isAllDay) {
        require(!endDayInclusive.isBefore(startDay)) {
            "Der Endtag darf nicht vor dem Starttag liegen."
        }
        startDay.atStartOfDay()
    } else {
        require(endTime.isAfter(startTime)) { "Das Ende muss nach dem Beginn liegen." }
        startDay.atTime(startTime)
    }
    val end = if (isAllDay) {
        endDayInclusive.plusDays(1).atStartOfDay()
    } else {
        startDay.atTime(endTime)
    }

    return NewCalendarEvent(
        title = normalizedTitle,
        start = start,
        end = end,
        isAllDay = isAllDay,
        location = location?.trim()?.ifBlank { null },
        invitePartner = invitePartner,
        recurrence = recurrence,
        blocksSharedFreeTime = isAllDay && blocksSharedFreeTime,
    )
}
