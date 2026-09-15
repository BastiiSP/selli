package com.prehmus.selli

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import com.prehmus.selli.domain.repository.ExpenseRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import com.prehmus.selli.domain.repository.LocationRepository
import com.prehmus.selli.domain.repository.NoteRepository
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import com.prehmus.selli.domain.repository.SessionRepository

/**
 * Verdrahtungspunkt zwischen UI (Claude) und Logik (Codex): MainActivity baut
 * hieraus die ViewModels. Die konkreten Implementierungen liefern die
 * Codex-Module unter data/ und domain/.
 */
interface AppDependencies {
    val googleCalendarRepository: GoogleCalendarRepository
    val icsCalendarRepository: IcsCalendarRepository
    val calendarMergeService: CalendarMergeService
    val calendarRepository: CalendarRepository
    val sessionRepository: SessionRepository
    val eventCustomizationRepository: EventCustomizationRepository

    /** Adressvorschläge für das Ortsfeld — liefert bei fehlendem Schlüssel einfach nichts. */
    val placeSuggestionRepository: PlaceSuggestionRepository

    /** Positionen beider Personen (Supabase). Ohne Zugangsdaten eine dauerhaft leere Quelle. */
    val locationRepository: LocationRepository

    /** Gemeinsame Ausgaben (Supabase). Ohne Zugangsdaten eine dauerhaft leere Quelle. */
    val expenseRepository: ExpenseRepository

    /** Gemeinsame Ideen-Ordner/-Punkte (Supabase). Ohne Zugangsdaten eine dauerhaft leere Quelle. */
    val noteRepository: NoteRepository

    /**
     * false, wenn in `local.properties` keine Supabase-Zugangsdaten liegen — dann zeigt der
     * Standort-Tab einen Hinweis statt einer leeren Karte, und es läuft kein Tracking.
     */
    val isLocationSharingConfigured: Boolean
}
