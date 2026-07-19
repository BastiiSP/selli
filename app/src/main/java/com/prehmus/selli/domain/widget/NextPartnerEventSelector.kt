package com.prehmus.selli.domain.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime

class NextPartnerEventSelector {
    fun select(events: List<CalendarEvent>, partner: Person, now: LocalDateTime): CalendarEvent? =
        events
            .asSequence()
            .filter { event -> event.owner == partner }
            .filter { event -> event.end > now }
            .sortedWith(
                compareBy<CalendarEvent> { event -> event.start }
                    .thenBy { event -> event.title }
                    .thenBy { event -> event.id },
            )
            .firstOrNull()
}
