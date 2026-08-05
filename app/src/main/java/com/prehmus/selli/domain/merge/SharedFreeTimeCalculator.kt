package com.prehmus.selli.domain.merge

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.countsAsBusy
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reine Berechnung der gemeinsamen freien Blöcke aus einer **bereits vorliegenden** Terminliste.
 *
 * Absichtlich vom [DefaultCalendarMergeService] getrennt und ohne `suspend`: Der Widget-Job holt
 * seine 30 Tage genau einmal und leitet daraus Zeile 1 (nächster Partnertermin), Zeile 2 (nächste
 * Wir-Zeit) und Zeile 3 (nächster freier Slot) ab — ohne zusätzlichen Netzwerk-Request pro Tag.
 */
object SharedFreeTimeCalculator {

    /** Fenster und Mindestdauer eines gemeinsamen freien Blocks — Quelle der Wahrheit für beide Pfade. */
    val WINDOW_START: LocalTime = LocalTime.of(9, 0)
    val WINDOW_END: LocalTime = LocalTime.of(22, 0)
    val MINIMUM_BLOCK: Duration = Duration.ofHours(3)

    /**
     * Liefert alle mindestens drei Stunden langen Blöcke im gemeinsamen Tagesfenster.
     * Ganztägige Events blockieren nur mit expliziter lokaler Festlegung, weil sie in den
     * verbundenen Kalendern häufig reine Marker wie Geburtstage sind.
     *
     * [events] darf ruhig Termine anderer Tage enthalten — die Zuschneidung auf das Tagesfenster
     * filtert sie ohnehin heraus.
     */
    fun freeBlocksOn(day: LocalDate, events: List<CalendarEvent>): List<FreeTimeBlock> {
        val window = TimeInterval(
            start = day.atTime(WINDOW_START),
            end = day.atTime(WINDOW_END),
        )
        val blockedIntervals = events
            .asSequence()
            .filter { event -> event.countsAsBusy }
            .mapNotNull { event -> event.blockedIntervalIn(window) }
            .sortedBy { interval -> interval.start }
            .toList()
            .mergeTouching()

        val free = mutableListOf<FreeTimeBlock>()
        var cursor = window.start
        for (blockedInterval in blockedIntervals) {
            if (Duration.between(cursor, blockedInterval.start) >= MINIMUM_BLOCK) {
                free += FreeTimeBlock(cursor, blockedInterval.start)
            }
            if (blockedInterval.end > cursor) {
                cursor = blockedInterval.end
            }
        }

        if (Duration.between(cursor, window.end) >= MINIMUM_BLOCK) {
            free += FreeTimeBlock(cursor, window.end)
        }
        return free
    }

    /** Dieselbe Rechnung für jeden Tag des Bereichs, in Datumsreihenfolge. */
    fun freeBlocksInRange(
        range: DateRange,
        events: List<CalendarEvent>,
    ): Map<LocalDate, List<FreeTimeBlock>> {
        val freeBlocksByDay = linkedMapOf<LocalDate, List<FreeTimeBlock>>()
        var day = range.start
        while (!day.isAfter(range.endInclusive)) {
            freeBlocksByDay[day] = freeBlocksOn(day, events)
            day = day.plusDays(1)
        }
        return freeBlocksByDay
    }

    private fun CalendarEvent.blockedIntervalIn(window: TimeInterval): TimeInterval? {
        val clippedStart = maxOf(start, window.start)
        val clippedEnd = minOf(end, window.end)

        return if (clippedStart < clippedEnd) {
            TimeInterval(start = clippedStart, end = clippedEnd)
        } else {
            null
        }
    }

    private fun List<TimeInterval>.mergeTouching(): List<TimeInterval> {
        if (isEmpty()) return emptyList()

        val merged = mutableListOf<TimeInterval>()
        var current = first()

        for (interval in drop(1)) {
            current = if (interval.start <= current.end) {
                current.copy(end = maxOf(current.end, interval.end))
            } else {
                merged += current
                interval
            }
        }

        merged += current
        return merged
    }

    private data class TimeInterval(
        val start: LocalDateTime,
        val end: LocalDateTime,
    )
}
