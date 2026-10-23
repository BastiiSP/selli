package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import io.github.jan.supabase.exceptions.RestException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Keine Android-Abhängigkeit und kein interaktiver Provider: auch im Hintergrund sicher. */
class SupabaseSessionCoordinator(
    private val backend: SupabaseAuthBackend,
    private val idTokenProvider: GoogleIdTokenProvider,
    scope: CoroutineScope,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val initializationTimeoutMillis: Long = 10_000L,
    private val reconfirmationRetryMillis: Long = 60_000L,
    private val expiryMarginMillis: Long = 60_000L,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) {
    private val mutex = Mutex()
    private val mutableAccessState = MutableStateFlow(CloudAccessState.CONNECTING)
    val accessState: StateFlow<CloudAccessState> = mutableAccessState.asStateFlow()

    // Aufrufer, die während eines Durchlaufs eintreffen, übernehmen auch dessen Fehler.
    @Volatile
    private var completedAttempt = 0L
    private var lastResult: Result<Unit> = Result.success(Unit)
    @Volatile
    private var sessionRevision = 0L
    private var lastReconfirmationAt: Long? = null
    private var failedStatus: SupabaseAuthStatus? = null
    @Volatile
    private var completedRecovery = 0L
    private var lastRecoveryResult: Result<Unit> = Result.success(Unit)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            backend.statusChanges.collect {
                mutex.withLock {
                    // Nicht einen inzwischen überholten Flow-Wert nach einem Sign-in anwenden.
                    applyStatus(backend.status)
                }
            }
        }
    }

    suspend fun ensureSignedIn(force: Boolean = false): Result<Unit> {
        val observedAttempt = completedAttempt
        return mutex.withLock {
            if (completedAttempt != observedAttempt) return@withLock lastResult
            completeAttempt { ensureLocked(force = force) }
        }
    }

    /** Nur nach einer abgelehnten Datenanfrage; force allein umgeht lediglich die Drosselung. */
    private suspend fun recoverSession(observedRevision: Long): Result<Unit> {
        val observedRecovery = completedRecovery
        return mutex.withLock {
            if (completedRecovery != observedRecovery) return@withLock lastRecoveryResult
            val result = completeAttempt {
                ensureLocked(force = true, refresh = sessionRevision == observedRevision)
            }
            lastRecoveryResult = result
            completedRecovery++
            result
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = mutex.withLock {
        completeAttempt {
            awaitReady()
            if (idToken.isBlank()) throw CloudSyncException.ReconfirmationRequired()
            backend.signInWithIdToken(idToken)
            sessionRevision++
            connected()
        }
    }

    /** Gemeinsamer, testbarer Ausführungspfad auch für SelliSupabaseClient.withSession. */
    suspend fun <T> withSession(block: suspend () -> T): Result<T> {
        ensureSignedIn().exceptionOrNull()?.let { return Result.failure(it) }
        val observedRevision = sessionRevision
        return try {
            val value = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!needsSessionRetry(error)) throw error
                recoverSession(observedRevision).getOrThrow()
                // Genau eine Wiederholung. Kein Replay bei Netzfehlern/5xx (Schreibaktionen!).
                block()
            }
            mutex.withLock { connected() }
            Result.success(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val classified = classifyCloudError(error)
            mutex.withLock { failed(classified) }
            logCloudError(logger, SOURCE_REQUEST, classified)
            Result.failure(classified)
        }
    }

    private suspend fun ensureLocked(force: Boolean, refresh: Boolean = false) {
        awaitReady()
        when (val status = backend.status) {
            SupabaseAuthStatus.Initializing -> throw CloudSyncException.Offline()
            SupabaseAuthStatus.NetworkFailure -> throw CloudSyncException.Offline()
            SupabaseAuthStatus.ServerFailure -> throw CloudSyncException.Failed()
            SupabaseAuthStatus.NotAuthenticated -> signInSilently(force)
            is SupabaseAuthStatus.Authenticated -> {
                if (!refresh && status.expiresAtMillis > nowMillis() + expiryMarginMillis) {
                    connected()
                    return
                }
                try {
                    backend.refreshCurrentSession()
                    sessionRevision++
                    connected()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (error is RestException && error.statusCode in 400..499) {
                        backend.clearSession()
                        signInSilently(force)
                    } else {
                        // Insbesondere Offline/5xx: weder Sitzung löschen noch Google-Login versuchen.
                        throw error
                    }
                }
            }
        }
    }

    private suspend fun awaitReady() {
        if (backend.status != SupabaseAuthStatus.Initializing) return
        val initialized = withTimeoutOrNull(initializationTimeoutMillis) {
            backend.awaitInitialization()
            backend.statusChanges.first { it != SupabaseAuthStatus.Initializing }
            true
        } ?: false
        if (!initialized) throw CloudSyncException.Offline()
    }

    private suspend fun signInSilently(force: Boolean) {
        val lastAttempt = lastReconfirmationAt
        if (!force && lastAttempt != null && nowMillis() - lastAttempt < reconfirmationRetryMillis) {
            throw CloudSyncException.ReconfirmationRequired()
        }
        if (lastReconfirmationAt != null) lastReconfirmationAt = nowMillis()
        val stored = idTokenProvider.lastGoogleIdToken()
            ?.takeIf { googleIdTokenIsFresh(it, nowMillis() + expiryMarginMillis) }
        val token = stored ?: try {
            idTokenProvider.freshGoogleIdTokenSilently().getOrThrow().takeUnless(String::isBlank)
                ?: throw CloudSyncException.ReconfirmationRequired()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val classified = classifyCloudError(error)
            if (classified is CloudSyncException.Offline) throw classified
            throw CloudSyncException.ReconfirmationRequired(error)
        }
        backend.signInWithIdToken(token)
        sessionRevision++
        connected()
    }

    private suspend fun completeAttempt(block: suspend () -> Unit): Result<Unit> {
        val result = try {
            block()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val classified = classifyCloudError(error)
            failed(classified)
            logCloudError(logger, SOURCE_AUTH, classified)
            Result.failure(classified)
        }
        lastResult = result
        completedAttempt++
        return result
    }

    private fun connected() {
        failedStatus = null
        lastReconfirmationAt = null
        mutableAccessState.value = CloudAccessState.CONNECTED
    }

    private fun failed(error: CloudSyncException) {
        failedStatus = backend.status
        if (error is CloudSyncException.ReconfirmationRequired) {
            // Gedrosselte Leseversuche dürfen die Sperrfrist nicht immer wieder verlängern.
            if (lastReconfirmationAt == null) {
                lastReconfirmationAt = nowMillis()
            }
            mutableAccessState.value = CloudAccessState.RECONFIRMATION_REQUIRED
        } else if (mutableAccessState.value != CloudAccessState.RECONFIRMATION_REQUIRED) {
            mutableAccessState.value = when (error) {
                is CloudSyncException.NotConfigured -> CloudAccessState.NOT_CONFIGURED
                else -> CloudAccessState.OFFLINE
            }
        }
    }

    private fun applyStatus(status: SupabaseAuthStatus) {
        if (status is SupabaseAuthStatus.Authenticated) {
            // Ein vor dem Anfragefehler emittiertes Authenticated darf dessen Zustand nicht
            // nachträglich überschreiben. Ein neuer SDK-Refresh oder expliziter Erfolg darf es.
            if (status != failedStatus) connected()
        } else if (mutableAccessState.value != CloudAccessState.RECONFIRMATION_REQUIRED) {
            mutableAccessState.value = when (status) {
                SupabaseAuthStatus.NetworkFailure, SupabaseAuthStatus.ServerFailure -> CloudAccessState.OFFLINE
                else -> CloudAccessState.CONNECTING
            }
        }
    }

    private companion object {
        const val SOURCE_AUTH = "Supabase-Anmeldung"
        const val SOURCE_REQUEST = "Supabase-Anfrage"
    }
}

/** Nur Ablauf-Vorprüfung, keine Signaturprüfung; die übernimmt Supabase beim Login. */
internal fun googleIdTokenIsFresh(token: String, validAfterMillis: Long): Boolean = try {
    val parts = token.split('.')
    if (parts.size != 3) {
        false
    } else {
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val exp = Json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull
        exp != null && exp > validAfterMillis / 1_000L
    }
} catch (_: IllegalArgumentException) {
    false
}
