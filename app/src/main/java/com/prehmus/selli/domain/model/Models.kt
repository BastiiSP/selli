package com.prehmus.selli.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class Person { BASTI, MELLI }

enum class CalendarSource { GOOGLE_OWN, GOOGLE_PARTNER, WORK_ICS }

data class Account(
    val id: String,
    val email: String,
    val displayName: String,
    val person: Person,
)

sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult
    data object Cancelled : AuthResult
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean,
    val source: CalendarSource,
    val owner: Person,
    val isSharedEvent: Boolean,
    val location: String? = null,
    val description: String? = null,
    val seriesId: String? = null,
    /** Titel/Zeit/Ort/Beschreibung wurden lokal überschrieben — treibt das "Angepasst"-Badge. */
    val isCustomized: Boolean = false,
    val category: EventCategory = EventCategory.PRIVATE,
    /**
     * Irgendeine lokale Anpassung liegt vor (Feld- oder reine Kategorie-Änderung) — treibt die
     * Sichtbarkeit von "Anpassung zurücksetzen". Eine reine Kategorie-Änderung zeigt bewusst
     * kein "Angepasst"-Badge (die Kategorie-Pill sagt das schon), bleibt aber zurücksetzbar.
     */
    val hasAnyCustomization: Boolean = false,
    /**
     * Wirksame Selli-Festlegung für die gemeinsame Frei-Zeit-Berechnung.
     * Quellen liefern für Ganztagstermine standardmäßig false; lokale Anpassungen können
     * den Wert beim Merge überschreiben. Für getimte Termine bleibt der Wert true.
     */
    val blocksSharedFreeTime: Boolean = !isAllDay,
)

data class NewCalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val invitePartner: Boolean = false,
    val recurrence: EventRecurrence? = null,
    /** Rein lokale Selli-Festlegung; wird nicht an Google übertragen. */
    val blocksSharedFreeTime: Boolean = isAllDay,
)

enum class EventCategory { WORK, PRIVATE, TOGETHER }
