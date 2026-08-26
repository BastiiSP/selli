package com.prehmus.selli.domain.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.countsAsBusy
import java.time.LocalDateTime

class NextSharedEventSelector {
    /**
     * Frühester noch nicht beendeter Termin mit `category == TOGETHER`, unabhängig vom Owner.
     * Nicht blockierende Ganztags-Marker bleiben außen vor — dieselbe Regel wie in der
     * Frei-Zeit-Berechnung.
     */
    fun select(events: List<CalendarEvent>, now: LocalDateTime): CalendarEvent? =
        selectUpcoming(events = events, now = now, limit = 1).firstOrNull()

    /**
     * Die nächsten [limit] noch nicht beendeten Wir-Zeit-Termine in chronologischer
     * Reihenfolge — Datenquelle für die Liste „Nächste gemeinsame Termine" auf dem
     * Homescreen. Filter und Sortierung sind bewusst identisch zu [select], damit
     * Countdown und Liste nie unterschiedliche Termine für „als nächstes" halten.
     */
    fun selectUpcoming(
        events: List<CalendarEvent>,
        now: LocalDateTime,
        limit: Int,
    ): List<CalendarEvent> = events
        .asSequence()
        .filter { event -> event.category == EventCategory.TOGETHER }
        .filter { event -> event.countsAsBusy }
        .filter { event -> event.end > now }
        .sortedWith(
            compareBy<CalendarEvent> { event -> event.start }
                .thenBy { event -> event.title }
                .thenBy { event -> event.id },
        )
        .take(limit)
        .toList()
}
