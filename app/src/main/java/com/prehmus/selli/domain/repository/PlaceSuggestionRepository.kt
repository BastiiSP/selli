package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.places.LocationSuggestion

/**
 * Adressvorschläge fürs Ortsfeld. Beide Aufrufe sind bewusst fehlertolerant: das Ortsfeld
 * ist optional und darf das Speichern nie blockieren — auch nicht ohne Netz oder Schlüssel.
 *
 * [sessionToken] klammert alle Tastendrücke bis zur Auswahl zu einem Vorgang zusammen.
 * Wird für jede neue Eingabe ein frischer Wert übergeben, rechnet Google Vorschläge und
 * die anschließende Detailabfrage als eine Sitzung ab statt einzeln.
 */
interface PlaceSuggestionRepository {
    /**
     * Adressvorschläge zur Eingabe. Wirft NIE: bei fehlendem Key, zu kurzer Eingabe,
     * fehlender Internetverbindung, HTTP-Fehler oder kaputtem JSON kommt eine leere Liste zurück.
     */
    suspend fun suggest(query: String, sessionToken: String? = null): List<LocationSuggestion>

    /**
     * Vollständige Anschrift zum ausgewählten Vorschlag. Liefert bei jedem Fehler null —
     * die UI bleibt dann bei [LocationSuggestion.fullText]. Wirft NIE.
     */
    suspend fun resolveFullAddress(placeId: String, sessionToken: String? = null): String?
}
