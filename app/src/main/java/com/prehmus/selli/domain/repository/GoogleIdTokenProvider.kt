package com.prehmus.selli.domain.repository

/**
 * Zugriff auf das rohe Google-ID-Token der letzten Anmeldung. Bewusst getrennt vom
 * übrigen Kalender-Vertrag: nur die Supabase-Kopplung des Standort-Features braucht es.
 */
interface GoogleIdTokenProvider {
    /** Rohes Google-ID-Token der letzten Anmeldung, oder null wenn keine vorliegt. */
    fun lastGoogleIdToken(): String?
}
