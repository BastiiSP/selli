package com.prehmus.selli.domain.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import java.time.LocalDateTime

class NextSharedEventSelector {
    /** Frühester noch nicht beendeter Termin mit `category == TOGETHER`, unabhängig vom Owner. */
    fun select(events: List<CalendarEvent>, now: LocalDateTime): CalendarEvent? =
        events
            .asSequence()
            .filter { event -> event.category == EventCategory.TOGETHER }
            .filter { event -> event.end > now }
            .sortedWith(
                compareBy<CalendarEvent> { event -> event.start }
                    .thenBy { event -> event.title }
                    .thenBy { event -> event.id },
            )
            .firstOrNull()
}
