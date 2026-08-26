package com.prehmus.selli.data.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die reine Token-Logik — analog zu [GoogleCalendarSessionTest], das dieselbe Trennung
 * nutzt. Das Repository selbst lässt sich im JVM-Unit-Test nicht instanziieren, weil sein
 * Konstruktor `CredentialManager.create()` aufruft; die testbaren Anteile liegen deshalb
 * bewusst als freie Funktionen in derselben Datei.
 */
class GoogleIdTokenProviderTest {

    @Test
    fun `missing token reads as no token`() {
        assertNull(googleIdTokenOrNull(null))
    }

    @Test
    fun `stored token is returned unchanged`() {
        assertEquals("eyJhbGciOi.header.signature", googleIdTokenOrNull("eyJhbGciOi.header.signature"))
    }

    @Test
    fun `blank stored token counts as no token`() {
        assertNull(googleIdTokenOrNull(""))
        assertNull(googleIdTokenOrNull("   "))
        assertNull(googleIdTokenOrNull("\n\t"))
    }

    @Test
    fun `reset does not cover the token key on its own`() {
        // Deshalb entfernt resetSession() den Token-Schlüssel zusätzlich und ausdrücklich:
        // er beginnt nicht mit "own_"/"partner_" und fiele sonst durch das Raster, womit
        // "Konto wechseln" die Supabase-Kopplung nicht vergessen würde.
        val keys = setOf("own_email", "partner_email", GOOGLE_ID_TOKEN_KEY)
        val reset = sessionKeysToReset(keys)

        assertTrue(reset.containsAll(setOf("own_email", "partner_email")))
        assertTrue(GOOGLE_ID_TOKEN_KEY !in reset)
    }

    private companion object {
        const val GOOGLE_ID_TOKEN_KEY = "google_id_token"
    }
}
