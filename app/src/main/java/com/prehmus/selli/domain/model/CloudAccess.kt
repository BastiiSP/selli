package com.prehmus.selli.domain.model

/** Zustand der gemeinsamen Cloud-Anmeldung (Supabase) für Ideen, Kosten und Standort. */
enum class CloudAccessState { NOT_CONFIGURED, CONNECTING, CONNECTED, OFFLINE, RECONFIRMATION_REQUIRED }

/** Fachliche Fehlerkategorie aller Supabase-Zugriffe. Die UI übersetzt den Typ in Text. */
sealed class CloudSyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Sitzung verloren und nicht still wiederherstellbar: Google-Konto erneut bestätigen. */
    class ReconfirmationRequired(cause: Throwable? = null) :
        CloudSyncException("Google-Konto muss erneut bestätigt werden.", cause)
    /** Netz/Server nicht erreichbar; vorhandene Sitzung bleibt erhalten. */
    class Offline(cause: Throwable? = null) : CloudSyncException("Server nicht erreichbar.", cause)
    class NotConfigured : CloudSyncException("Supabase ist nicht konfiguriert.")
    /** Sonstiger Serverfehler (5xx, RLS-Ablehnung, Dekodierfehler …). */
    class Failed(cause: Throwable? = null) : CloudSyncException("Serveranfrage fehlgeschlagen.", cause)
}

/** Ergebnis der interaktiven Bestätigung des bestehenden Google-Kontos. */
sealed interface CloudReconfirmResult {
    data object Success : CloudReconfirmResult
    data object Cancelled : CloudReconfirmResult
    /** Ein anderes Google-Konto wurde gewählt; nichts wurde geändert. */
    data class WrongAccount(val expectedEmail: String) : CloudReconfirmResult
    data class Failed(val error: CloudSyncException) : CloudReconfirmResult
}

/** Ereignisse des Standort-Feeds; der Feed läuft nach Fehlern weiter. */
sealed interface LocationFeedEvent {
    data class Loaded(val locations: List<PersonLocation>) : LocationFeedEvent
    data class Failed(val error: CloudSyncException) : LocationFeedEvent
}
