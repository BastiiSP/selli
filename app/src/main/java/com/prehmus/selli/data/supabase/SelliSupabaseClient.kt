package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelliSupabaseClient(
    private val supabaseUrl: String,
    private val supabaseAnonKey: String,
    private val idTokenProvider: GoogleIdTokenProvider,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) {
    val isConfigured: Boolean = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val unconfiguredState = MutableStateFlow(CloudAccessState.NOT_CONFIGURED).asStateFlow()

    /** Lazy: Auth braucht den durch androidx.startup bereitgestellten Anwendungskontext. */
    val client: SupabaseClient? by lazy {
        if (!isConfigured) null else createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey,
        ) {
            install(Auth) {
                // ProcessLifecycleOwner.onStop würde sonst auch den Standort-Foreground-Service
                // auf Initializing setzen und dessen Auto-Refresh bei jedem App-Wechsel stoppen.
                enableLifecycleCallbacks = false
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Realtime)
        }
    }

    private val coordinator: SupabaseSessionCoordinator? by lazy {
        client?.let {
            SupabaseSessionCoordinator(SupabaseKtAuthBackend(it.auth), idTokenProvider, scope, logger = logger)
        }
    }

    val accessState: StateFlow<CloudAccessState> get() = coordinator?.accessState ?: unconfiguredState

    suspend fun ensureSignedIn(force: Boolean = false): Result<Unit> = cloudResult {
        coordinator?.ensureSignedIn(force) ?: Result.failure(CloudSyncException.NotConfigured())
    }

    suspend fun <T> withSession(block: suspend (SupabaseClient) -> T): Result<T> = cloudResult {
        val configuredClient = client ?: return@cloudResult Result.failure(CloudSyncException.NotConfigured())
        checkNotNull(coordinator).withSession { block(configuredClient) }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = cloudResult {
        coordinator?.signInWithGoogleIdToken(idToken) ?: Result.failure(CloudSyncException.NotConfigured())
    }

    /** Auch Fehler beim verzögerten Aufbau des SDK-Clients bleiben im fachlichen Fehlervertrag. */
    private suspend fun <T> cloudResult(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        logCloudError(logger, "Supabase-Client", error)
        Result.failure(classifyCloudError(error))
    }

    suspend fun currentUserId(): String? = try {
        client?.auth?.currentSessionOrNull()?.user?.id
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        logCloudError(logger, "Supabase-Sitzung", error)
        null
    }
}
