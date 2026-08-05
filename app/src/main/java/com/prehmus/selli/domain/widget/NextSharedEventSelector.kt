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
        events
            .asSequence()
            .filter { event -> event.category == EventCategory.TOGETHER }
            .filter { event -> event.countsAsBusy }
            .filter { event -> event.end > now }
            .sortedWith(
                compareBy<CalendarEvent> { event -> event.start }
                    .thenBy { event -> event.title }
                    .thenBy { event -> event.id },
            )
            .firstOrNull()
}
