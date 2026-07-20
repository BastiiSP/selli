package com.prehmus.selli.ui.calendar

/**
 * Die drei umschaltbaren Zeitraum-Darstellungen des Kalenderbereichs zwischen
 * Header und Terminliste. Monat bleibt das vertraute Raster; Woche und Tag
 * zeigen einen echten 24-Stunden-Zeitstrahl (siehe [TimelineView]).
 */
enum class CalendarViewMode {
    MONTH,
    WEEK,
    DAY,
}
