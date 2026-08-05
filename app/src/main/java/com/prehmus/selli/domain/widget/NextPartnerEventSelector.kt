package com.prehmus.selli.domain.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.countsAsBusy
import java.time.LocalDateTime

class NextPartnerEventSelector {
    /**
     * Frühester noch nicht beendeter Termin des Partners. Nicht blockierende Ganztags-Marker
     * (z. B. Geburtstage) bleiben außen vor, damit sie keine echten Termine aus dem Widget
     * verdrängen — dieselbe Regel wie in der Frei-Zeit-Berechnung.
     */
    fun select(events: List<CalendarEvent>, partner: Person, now: LocalDateTime): CalendarEvent? =
        events
            .asSequence()
            .filter { event -> event.owner == partner }
            .filter { event -> event.countsAsBusy }
            .filter { event -> event.end > now }
            .sortedWith(
                compareBy<CalendarEvent> { event -> event.start }
                    .thenBy { event -> event.title }
                    .thenBy { event -> event.id },
            )
            .firstOrNull()
}
