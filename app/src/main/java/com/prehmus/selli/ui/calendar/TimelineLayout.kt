package com.prehmus.selli.ui.calendar

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Ein Tag hat 24 × 60 = 1440 Minuten — die Bezugsgröße für den Zeitstrahl. */
const val MINUTES_PER_DAY = 24 * 60

/**
 * Ein zeitlich verorteter Termin im Tages-Zeitstrahl. [startMinute]/[endMinute]
 * sind auf den jeweiligen Tag zugeschnitten (0…1440), sodass über Mitternacht
 * laufende Termine an ihrem Tagesrand kleben. [column] von [columnCount] regelt,
 * wie sich zeitgleiche Termine die Breite teilen (nebeneinander statt überlappend).
 */
data class PositionedEvent(
    val event: CalendarEvent,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
) {
    /** Sichtbare Dauer in Minuten, mindestens 1 (damit auch Null-Dauer-Termine sichtbar bleiben). */
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(1)
}

/**
 * Das fertige Layout eines Tages: ganztägige/über den ganzen Tag laufende Termine
 * getrennt in [allDay] (eigener schmaler Bereich oberhalb des Rasters), der Rest
 * als [timed] mit fertiger Spalten-Zuteilung.
 */
data class DayTimeline(
    val allDay: List<CalendarEvent>,
    val timed: List<PositionedEvent>,
)

/**
 * Reine Layout-Berechnung für einen Tag (ohne Compose, damit testbar):
 * trennt ganztägige von terminierten Ereignissen, schneidet terminierte auf den
 * Tag zu und teilt sich überschneidende Termine in nebeneinanderliegende Spalten.
 */
fun layoutDay(events: List<CalendarEvent>, day: LocalDate): DayTimeline {
    val dayStart = day.atStartOfDay()
    val dayEnd = dayStart.plusDays(1)

    val allDay = mutableListOf<CalendarEvent>()
    val timedRaw = mutableListOf<PositionedEvent>()

    for (event in events) {
        if (event.isAllDay || spansEntireDay(event, dayStart, dayEnd)) {
            allDay += event
            continue
        }
        val rawStart = minuteOfDay(event.start, dayStart)
        val rawEnd = minuteOfDay(event.end, dayStart)
        val startMinute = rawStart.coerceIn(0, MINUTES_PER_DAY)
        val endMinute = rawEnd.coerceIn(0, MINUTES_PER_DAY)
        // Termine, deren sichtbarer Anteil an diesem Tag leer ist (enden vor Tagesbeginn
        // oder beginnen nach Tagesende), überspringen. Null-Dauer-Punkte am Tag bleiben sichtbar.
        if (endMinute < startMinute || (endMinute == startMinute && rawEnd <= 0)) continue
        timedRaw += PositionedEvent(
            event = event,
            startMinute = startMinute,
            endMinute = endMinute,
            column = 0,
            columnCount = 1,
        )
    }

    return DayTimeline(
        allDay = allDay.sortedBy { it.start },
        timed = assignColumns(timedRaw),
    )
}

/**
 * Weist sich überschneidenden Terminen Spalten zu. Termine werden in „Cluster"
 * transitiv überlappender Ereignisse gruppiert; innerhalb eines Clusters teilen
 * sich alle die volle Breite gleichmäßig, jeder bekommt eine feste Spalte.
 */
private fun assignColumns(events: List<PositionedEvent>): List<PositionedEvent> {
    if (events.isEmpty()) return emptyList()
    val sorted = events.sortedWith(compareBy({ it.startMinute }, { it.endMinute }))

    val result = mutableListOf<PositionedEvent>()
    var cluster = mutableListOf<PositionedEvent>()
    var clusterEnd = Int.MIN_VALUE

    fun flushCluster() {
        if (cluster.isEmpty()) return
        // Greedy: jede Spalte merkt sich, bis wann sie belegt ist.
        val columnEnds = mutableListOf<Int>()
        val withColumn = cluster.map { ev ->
            val col = columnEnds.indexOfFirst { it <= ev.startMinute }
            val assigned = if (col == -1) {
                columnEnds += ev.effectiveEnd()
                columnEnds.size - 1
            } else {
                columnEnds[col] = ev.effectiveEnd()
                col
            }
            ev to assigned
        }
        val columnCount = columnEnds.size
        withColumn.forEach { (ev, col) ->
            result += ev.copy(column = col, columnCount = columnCount)
        }
        cluster = mutableListOf()
        clusterEnd = Int.MIN_VALUE
    }

    for (ev in sorted) {
        if (cluster.isNotEmpty() && ev.startMinute >= clusterEnd) {
            flushCluster()
        }
        cluster += ev
        clusterEnd = maxOf(clusterEnd, ev.effectiveEnd())
    }
    flushCluster()

    return result
}

/** Für die Überlappungsprüfung zählt eine Mindesthöhe, sonst „berühren" sich kurze Termine nie. */
private fun PositionedEvent.effectiveEnd(): Int = maxOf(endMinute, startMinute + 1)

private fun spansEntireDay(event: CalendarEvent, dayStart: LocalDateTime, dayEnd: LocalDateTime): Boolean =
    !event.start.isAfter(dayStart) && !event.end.isBefore(dayEnd)

private fun minuteOfDay(time: LocalDateTime, dayStart: LocalDateTime): Int =
    java.time.Duration.between(dayStart, time).toMinutes()
        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        .toInt()

// --- Zeitraum & Navigation je Ansicht -------------------------------------

/** Die sieben Tage (Mo–So) der Woche, in der [anchor] liegt. */
fun weekDays(anchor: LocalDate): List<LocalDate> {
    val monday = anchor.minusDays(((anchor.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

/**
 * Der Datumsbereich, den die Kalenderquelle für die aktive Ansicht abdecken muss —
 * jeweils mit etwas Puffer, damit Wochenüberhänge und Nachbartage gefüllt sind.
 */
fun fetchRangeFor(mode: CalendarViewMode, visibleMonth: YearMonth, anchorDay: LocalDate): DateRange =
    when (mode) {
        CalendarViewMode.MONTH -> DateRange(
            start = visibleMonth.atDay(1).minusDays(7),
            endInclusive = visibleMonth.atEndOfMonth().plusDays(7),
        )
        CalendarViewMode.WEEK -> {
            val week = weekDays(anchorDay)
            DateRange(start = week.first().minusDays(1), endInclusive = week.last().plusDays(1))
        }
        CalendarViewMode.DAY -> DateRange(
            start = anchorDay.minusDays(1),
            endInclusive = anchorDay.plusDays(1),
        )
    }
