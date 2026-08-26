package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft nur, was ohne Netzwerk und ohne Android-Framework prüfbar ist. Alles, was den
 * eigentlichen Supabase-Client baut, ist hier bewusst NICHT getestet: `install(Auth)` braucht
 * für seinen Sitzungsspeicher den Anwendungskontext, den supabase-kt sich über androidx.startup
 * holt — im JVM-Unit-Test existiert der nicht. Der Anmeldepfad wird am Gerät verifiziert.
 */
class SelliSupabaseClientTest {
    @Test
    fun `configured when url and key are set`() {
        val client = createClient(url = VALID_URL, key = "anon-key")

        assertTrue(client.isConfigured)
    }

    @Test
    fun `not configured when url is blank`() {
        assertFalse(createClient(url = "", key = "anon-key").isConfigured)
    }

    @Test
    fun `not configured when key is blank`() {
        assertFalse(createClient(url = VALID_URL, key = "").isConfigured)
    }

    @Test
    fun `not configured when url and key are blank`() {
        assertFalse(createClient(url = "", key = "").isConfigured)
    }

    @Test
    fun `unconfigured client fails safely without a session`() = runTest {
        val client = createClient(url = "", key = "")

        assertNull(client.client)
        assertTrue(client.ensureSignedIn().isFailure)
        assertNull(client.currentUserId())
    }

    private fun createClient(
        url: String,
        key: String,
        idToken: String? = null,
    ) = SelliSupabaseClient(
        supabaseUrl = url,
        supabaseAnonKey = key,
        idTokenProvider = FakeGoogleIdTokenProvider(idToken),
    )

    private class FakeGoogleIdTokenProvider(
        private val idToken: String?,
    ) : GoogleIdTokenProvider {
        override fun lastGoogleIdToken(): String? = idToken
    }

    private companion object {
        const val VALID_URL = "https://example.supabase.co"
    }
}
