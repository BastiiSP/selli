package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.key
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface SharedEventChange {
    val event: CalendarEvent

    data class New(override val event: CalendarEvent) : SharedEventChange

    data class Updated(
        override val event: CalendarEvent,
        /** Menschenlesbare deutsche Beschreibungen der geänderten Werte, in fester Reihenfolge. */
        val changedFields: List<String>,
    ) : SharedEventChange
}

data class SharedEventDetectionResult(
    val changes: List<SharedEventChange>,
    val updatedFingerprints: Map<EventKey, SharedEventFingerprint>,
)

class PartnerSharedEventChangeDetector {
    /**
     * @param currentEvents die zusammengeführten Termine des aktuellen Vorausschau-Fensters
     * @param partner die Person des/der Partner:in — nur DEREN Wir-Zeit-Termine lösen etwas aus
     * @param previouslySeen Fingerprints des letzten Laufs (beim ersten Lauf leer)
     */
    fun detect(
        currentEvents: List<CalendarEvent>,
        partner: Person,
        previouslySeen: Map<EventKey, SharedEventFingerprint>,
    ): SharedEventDetectionResult {
        val relevantEvents = currentEvents.filter { event ->
            event.category == EventCategory.TOGETHER && event.owner == partner
        }
        val updatedFingerprints = relevantEvents.associate { event ->
            event.key() to event.fingerprint()
        }
        val changes = relevantEvents.mapNotNull { event ->
            val previous = previouslySeen[event.key()]
                ?: return@mapNotNull SharedEventChange.New(event)
            val changedFields = changedFields(event, previous)

            if (changedFields.isEmpty()) {
                null
            } else {
                SharedEventChange.Updated(event, changedFields)
            }
        }.sortedWith(
            compareBy<SharedEventChange>(
                { it.event.start },
                { it.event.title },
                { it.event.id },
            ),
        )

        return SharedEventDetectionResult(
            changes = changes,
            updatedFingerprints = updatedFingerprints,
        )
    }

    private fun changedFields(
        event: CalendarEvent,
        previous: SharedEventFingerprint,
    ): List<String> =
        buildList {
            if (event.title != previous.title) {
                add("Neuer Titel: ${event.title}")
            }
            if (
                event.start != previous.start ||
                event.end != previous.end ||
                event.isAllDay != previous.isAllDay
            ) {
                add(formatChangedTime(event, previous))
            }
            if (event.location != previous.location) {
                add(
                    if (event.location.isNullOrBlank()) {
                        "Ort entfernt"
                    } else {
                        "Neuer Ort: ${event.location}"
                    },
                )
            }
            if (event.description != previous.description) {
                add("Beschreibung aktualisiert")
            }
        }

    private fun formatChangedTime(
        event: CalendarEvent,
        previous: SharedEventFingerprint,
    ): String {
        if (event.isAllDay) {
            return "Neue Zeit: ${event.start.toLocalDate().format(ALL_DAY_FORMATTER)}"
        }

        val dateChanged =
            event.start.toLocalDate() != previous.start.toLocalDate() ||
                event.end.toLocalDate() != previous.end.toLocalDate()
        val time = "${event.start.format(TIME_FORMATTER)}–${event.end.format(TIME_FORMATTER)}"

        return if (dateChanged) {
            "Neue Zeit: ${event.start.format(DATE_TIME_FORMATTER)}–${event.end.format(TIME_FORMATTER)}"
        } else {
            "Neue Zeit: $time"
        }
    }

    private companion object {
        val ALL_DAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, d. MMMM, HH:mm", Locale.GERMAN)
    }
}
