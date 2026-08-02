package com.prehmus.selli.domain.places

/**
 * Ein Adressvorschlag. [fullText] ist der Text, den die Vorschlagsliste selbst liefert —
 * er reicht als Ort, ist aber oft noch verkürzt ("Marienplatz 1, München-Altstadt-Lehel").
 * Die vollständige Anschrift holt erst `PlaceSuggestionRepository.resolveFullAddress`
 * über die [placeId]. Die ID lebt nur bis zur Auswahl und wird nirgends gespeichert.
 */
data class LocationSuggestion(
    val placeId: String,
    val primaryText: String, // z. B. "Musterstraße 1"
    val secondaryText: String?, // z. B. "12345 Berlin, Deutschland"
    val fullText: String, // z. B. "Musterstraße 1, 12345 Berlin"
)
