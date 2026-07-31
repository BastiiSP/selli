package com.prehmus.selli.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Ein konkreter gemeinsamer freier Slot: an welchem Tag, in welchem Zeitfenster. */
data class FreeSlot(
    val day: LocalDate,
    val block: FreeTimeBlock,
)

data class WidgetSnapshot(
    val partnerPerson: Person,
    val partnerDisplayName: String,
    val nextEvent: CalendarEvent?,
    val updatedAt: LocalDateTime,
    /** Nächster Wir-Zeit-Termin im Vorausschau-Fenster, unabhängig vom Owner (Zeile 2, ab Mittel). */
    val nextSharedEvent: CalendarEvent? = null,
    /** Nächster gemeinsamer freier Slot im Vorausschau-Fenster (Zeile 3, nur Groß). */
    val nextFreeSlot: FreeSlot? = null,
)
