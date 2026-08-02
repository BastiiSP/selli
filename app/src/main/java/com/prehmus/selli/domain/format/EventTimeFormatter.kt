package com.prehmus.selli.domain.format

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object EventTimeFormatter {
    /** true, wenn der Termin sichtbar über mehr als einen Kalendertag geht. */
    fun spansMultipleDays(
        start: LocalDateTime,
        end: LocalDateTime,
        isAllDay: Boolean,
    ): Boolean {
        val inclusiveEndDate = if (isAllDay) {
            inclusiveEndDate(start, end)
        } else {
            end.toLocalDate()
        }
        return inclusiveEndDate != start.toLocalDate()
    }

    /** Zeitspanne als Text für Terminliste und Termin-Detail. */
    fun formatRange(
        start: LocalDateTime,
        end: LocalDateTime,
        isAllDay: Boolean,
    ): String {
        if (isAllDay) {
            if (!spansMultipleDays(start, end, isAllDay = true)) return "ganztägig"

            return "ganztägig, ${start.toLocalDate().format(DateFormat)} – " +
                inclusiveEndDate(start, end).format(DateFormat)
        }

        if (!spansMultipleDays(start, end, isAllDay = false)) {
            return "${start.toLocalTime().format(TimeFormat)} – " +
                end.toLocalTime().format(TimeFormat)
        }

        return "${start.format(DateTimeFormat)} – ${end.format(DateTimeFormat)}"
    }

    private fun inclusiveEndDate(start: LocalDateTime, end: LocalDateTime): LocalDate =
        if (
            end.toLocalTime() == LocalTime.MIDNIGHT &&
            end.toLocalDate().isAfter(start.toLocalDate())
        ) {
            end.toLocalDate().minusDays(1)
        } else {
            end.toLocalDate()
        }

    private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private val DateFormat = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
    private val DateTimeFormat = DateTimeFormatter.ofPattern("EEE, d. MMM HH:mm", Locale.GERMAN)
}
