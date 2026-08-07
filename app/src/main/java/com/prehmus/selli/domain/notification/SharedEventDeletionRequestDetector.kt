package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.key

data class SharedEventDeletionRequestResult(
    val changes: List<SharedEventChange.DeleteRequested>,
    /** Alle aktuell offenen Anfragen — beim nächsten Lauf als [alreadyNotified] übergeben. */
    val updatedNotified: Set<EventKey>,
)

/**
 * Erkennt, wenn ein **eigener** Wir-Zeit-Termin neu die Lösch-Anfrage-Markierung des
 * Partners trägt — Gegenstück zu [PartnerSharedEventChangeDetector], das bewusst nur
 * Termine des Partners betrachtet, während eine Lösch-Anfrage auf den eigenen liegt.
 */
class SharedEventDeletionRequestDetector {
    fun detect(
        currentEvents: List<CalendarEvent>,
        self: Person,
        alreadyNotified: Set<EventKey>,
    ): SharedEventDeletionRequestResult {
        val requested = currentEvents.filter { event ->
            event.owner == self &&
                event.category == EventCategory.TOGETHER &&
                event.deleteRequestedBy != null
        }
        val changes = requested
            .filter { event -> event.key() !in alreadyNotified }
            .map { event -> SharedEventChange.DeleteRequested(event, requireNotNull(event.deleteRequestedBy)) }

        return SharedEventDeletionRequestResult(
            changes = changes,
            updatedNotified = requested.map { it.key() }.toSet(),
        )
    }
}
