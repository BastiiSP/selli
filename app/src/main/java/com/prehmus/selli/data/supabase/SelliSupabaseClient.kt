package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

class SelliSupabaseClient(
    private val supabaseUrl: String,
    private val supabaseAnonKey: String,
    private val idTokenProvider: GoogleIdTokenProvider,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) {
    /** false, wenn URL oder Key fehlen — dann bleibt das Standort-Feature inaktiv. */
    val isConfigured: Boolean = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    /**
     * Der rohe Client, oder null wenn nicht konfiguriert.
     *
     * Bewusst `lazy`: der Client zieht Ktor und den Auth-Sitzungsspeicher hoch. Ohne die
     * Verzögerung passierte das bei jedem App-Start, auch wenn der Standort-Tab nie geöffnet
     * wird. `install(Auth)` braucht dafür den Anwendungskontext, den supabase-kt sich über
     * androidx.startup (`SupabaseInitializer`) selbst holt — der steht erst nach dem
     * Prozessstart bereit.
     */
    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey,
            ) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
            }
        }
    }

    /**
     * Stellt sicher, dass eine Supabase-Session besteht. Eine bestehende Session gewinnt —
     * supabase-kt erneuert sie über ihren Refresh-Token selbst, das kurzlebige
     * Google-ID-Token wird also nur beim ersten Mal gebraucht.
     */
    suspend fun ensureSignedIn(): Result<Unit> {
        val configuredClient = client
            ?: return Result.failure(IllegalStateException("Supabase ist nicht konfiguriert."))

        return try {
            if (configuredClient.auth.currentSessionOrNull() != null) {
                Result.success(Unit)
            } else {
                val idToken = idTokenProvider.lastGoogleIdToken()
                    ?: return Result.failure(
                        IllegalStateException("Kein Google-ID-Token für die Supabase-Anmeldung verfügbar."),
                    )
                configuredClient.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                }
                Result.success(Unit)
            }
        } catch (error: Throwable) {
            logError(SOURCE_AUTH, error)
            Result.failure(error)
        }
    }

    /** Eigene Supabase-User-ID; null, solange keine Session besteht. */
    suspend fun currentUserId(): String? = try {
        client?.auth?.currentSessionOrNull()?.user?.id
    } catch (error: Throwable) {
        logError(SOURCE_SESSION, error)
        null
    }

    private fun logError(source: String, error: Throwable) {
        runCatching { logger.error(source, error) }
    }

    private companion object {
        const val SOURCE_AUTH = "Supabase-Anmeldung"
        const val SOURCE_SESSION = "Supabase-Sitzung"
    }
}
