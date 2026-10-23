package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseSessionCoordinatorTest {
    @Test
    fun `cold start waits for stored session without Google sign in`() = runTest {
        val backend = FakeBackend(SupabaseAuthStatus.Initializing)
        val tokens = FakeTokens()
        val coordinator = coordinator(backend, tokens)
        val result = async { coordinator.ensureSignedIn() }
        runCurrent()
        assertFalse(result.isCompleted)
        backend.states.value = authenticated()
        assertTrue(result.await().isSuccess)
        assertEquals(0, backend.signIns)
        assertEquals(0, tokens.silentCalls)
        assertEquals(CloudAccessState.CONNECTED, coordinator.accessState.value)
    }

    @Test
    fun `expired session refreshes exactly once`() = runTest {
        val backend = FakeBackend(SupabaseAuthStatus.Authenticated(NOW - 1))
        val coordinator = coordinator(backend)
        assertTrue(coordinator.ensureSignedIn().isSuccess)
        assertEquals(1, backend.refreshes)
        assertEquals(0, backend.signIns)
    }

    @Test
    fun `refresh 400 clears session and signs in silently`() = runTest {
        val backend = FakeBackend(SupabaseAuthStatus.Authenticated(NOW - 1))
        backend.refreshError = cloudHttpError(400, "Invalid Refresh Token", "/auth/v1/token")
        val tokens = FakeTokens()
        assertTrue(coordinator(backend, tokens).ensureSignedIn().isSuccess)
        assertEquals(1, backend.refreshes)
        assertEquals(1, backend.clears)
        assertEquals(1, tokens.silentCalls)
        assertEquals(listOf("fresh-token"), backend.usedTokens)
    }

    @Test
    fun `silent token unavailable requires reconfirmation without interactive capability`() = runTest {
        val tokens = FakeTokens(silent = Result.failure(IllegalStateException("Consent required")))
        val backend = FakeBackend()
        val coordinator = coordinator(backend, tokens)
        assertTrue(coordinator.ensureSignedIn().exceptionOrNull() is CloudSyncException.ReconfirmationRequired)
        assertEquals(CloudAccessState.RECONFIRMATION_REQUIRED, coordinator.accessState.value)
        assertEquals(0, backend.signIns)
        // Der Coordinator akzeptiert ausschließlich GoogleIdTokenProvider, keinen interaktiven Port.
    }

    @Test
    fun `expired saved token is skipped`() = runTest {
        val backend = FakeBackend()
        val tokens = FakeTokens(stored = jwt(NOW / 1_000 - 1))
        assertTrue(coordinator(backend, tokens).ensureSignedIn().isSuccess)
        assertEquals(1, tokens.silentCalls)
        assertEquals(listOf("fresh-token"), backend.usedTokens)
    }

    @Test
    fun `valid saved token is used without silent fetch`() = runTest {
        val backend = FakeBackend()
        val stored = jwt(NOW / 1_000 + 3_600)
        val tokens = FakeTokens(stored = stored)
        assertTrue(coordinator(backend, tokens).ensureSignedIn().isSuccess)
        assertEquals(0, tokens.silentCalls)
        assertEquals(listOf(stored), backend.usedTokens)
    }

    @Test
    fun `network refresh failure keeps session and recovers when SDK authenticates`() = runTest {
        val backend = FakeBackend(SupabaseAuthStatus.NetworkFailure)
        val tokens = FakeTokens()
        val coordinator = coordinator(backend, tokens)
        assertTrue(coordinator.ensureSignedIn().exceptionOrNull() is CloudSyncException.Offline)
        assertEquals(0, backend.clears)
        assertEquals(0, tokens.silentCalls)
        backend.states.value = authenticated()
        runCurrent()
        assertEquals(CloudAccessState.CONNECTED, coordinator.accessState.value)
        assertTrue(coordinator.ensureSignedIn().isSuccess)
    }

    @Test
    fun `ten simultaneous callers share one sign in`() = runTest {
        val backend = FakeBackend().apply { signInDelayMillis = 100 }
        val tokens = FakeTokens()
        val coordinator = coordinator(backend, tokens)
        val results = List(10) { async { coordinator.ensureSignedIn() } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, backend.signIns)
        assertEquals(1, tokens.silentCalls)
    }

    @Test
    fun `reconfirmation throttles for sixty seconds and force bypasses throttle`() = runTest {
        val tokens = FakeTokens(silent = Result.failure(IllegalStateException("Consent required")))
        val coordinator = coordinator(FakeBackend(), tokens)
        coordinator.ensureSignedIn()
        advanceTimeBy(30_000)
        coordinator.ensureSignedIn()
        assertEquals(1, tokens.silentCalls)
        coordinator.ensureSignedIn(force = true)
        assertEquals(2, tokens.silentCalls)
        advanceTimeBy(30_001)
        coordinator.ensureSignedIn()
        assertEquals(2, tokens.silentCalls)
        advanceTimeBy(30_000)
        coordinator.ensureSignedIn()
        assertEquals(3, tokens.silentCalls)
    }

    @Test
    fun `stuck initialization times out offline without a token login`() = runTest {
        val backend = FakeBackend(SupabaseAuthStatus.Initializing)
        val tokens = FakeTokens()
        val coordinator = coordinator(backend, tokens)
        assertTrue(coordinator.ensureSignedIn().exceptionOrNull() is CloudSyncException.Offline)
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals(0, backend.signIns)
        assertEquals(0, tokens.silentCalls)
    }

    @Test
    fun `withSession retries a 401 exactly once after refresh`() = runTest {
        val unauthorized = cloudHttpError(401, "JWT expired")
        val backend = FakeBackend(authenticated())
        val coordinator = coordinator(backend)
        var requests = 0
        val result = coordinator.withSession {
            requests++
            if (requests == 1) throw unauthorized
            "loaded"
        }
        assertEquals("loaded", result.getOrThrow())
        assertEquals(2, requests)
        assertEquals(1, backend.refreshes)
    }

    @Test
    fun `second 401 is returned without a third request`() = runTest {
        val unauthorized = cloudHttpError(401, "JWT expired")
        val backend = FakeBackend(authenticated())
        val coordinator = coordinator(backend)
        var requests = 0
        val result = coordinator.withSession<Unit> { requests++; throw unauthorized }
        assertTrue(result.exceptionOrNull() is CloudSyncException.ReconfirmationRequired)
        assertEquals(2, requests)
        assertEquals(1, backend.refreshes)
        runCurrent()
        assertEquals(CloudAccessState.RECONFIRMATION_REQUIRED, coordinator.accessState.value)
    }

    @Test
    fun `concurrent rejected requests share recovery of their session`() = runTest {
        val unauthorized = cloudHttpError(401, "JWT expired")
        val backend = FakeBackend(authenticated())
        val coordinator = coordinator(backend)
        val allStarted = CompletableDeferred<Unit>()
        var started = 0
        val results = List(10) {
            async {
                var calls = 0
                val result = coordinator.withSession {
                    calls++
                    if (calls == 1) {
                        started++
                        if (started == 10) allStarted.complete(Unit)
                        allStarted.await()
                        throw unauthorized
                    }
                    "loaded"
                }
                assertEquals(2, calls)
                result
            }
        }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, backend.refreshes)
    }

    @Test
    fun `network failure never replays a write`() = runTest {
        val backend = FakeBackend(authenticated())
        val coordinator = coordinator(backend)
        var requests = 0
        val result = coordinator.withSession<Unit> { requests++; throw IOException("offline") }
        assertTrue(result.exceptionOrNull() is CloudSyncException.Offline)
        assertEquals(1, requests)
        assertEquals(0, backend.refreshes)
    }

    @Test
    fun `bad ID token requires confirmation and throttles further sign ins`() = runTest {
        val backend = FakeBackend().apply {
            signInError = cloudHttpError(400, "Bad ID token", "/auth/v1/token")
        }
        val coordinator = coordinator(backend)
        assertTrue(coordinator.ensureSignedIn().exceptionOrNull() is CloudSyncException.ReconfirmationRequired)
        coordinator.ensureSignedIn()
        assertEquals(1, backend.signIns)
        assertEquals(CloudAccessState.RECONFIRMATION_REQUIRED, coordinator.accessState.value)
    }

    @Test
    fun `manual refresh offline and server errors preserve the session`() = runTest {
        for (error in listOf(IOException("offline"), cloudHttpError(500, "server error"))) {
            val backend = FakeBackend(SupabaseAuthStatus.Authenticated(NOW - 1)).apply { refreshError = error }
            val tokens = FakeTokens()
            val result = coordinator(backend, tokens).ensureSignedIn()
            assertTrue(result.isFailure)
            assertEquals(0, backend.clears)
            assertEquals(0, tokens.silentCalls)
            assertTrue(backend.status is SupabaseAuthStatus.Authenticated)
        }
    }

    @Test
    fun `SDK server refresh failure is Failed without clearing session`() = runTest {
        val backend = FakeBackend(SupabaseAuthStatus.ServerFailure)
        assertTrue(coordinator(backend).ensureSignedIn().exceptionOrNull() is CloudSyncException.Failed)
        assertEquals(0, backend.clears)
    }

    @Test
    fun `offline silent token is not reconfirmation and is not throttled`() = runTest {
        val tokens = FakeTokens(silent = Result.failure(IOException("offline")))
        val coordinator = coordinator(FakeBackend(), tokens)
        repeat(2) {
            assertTrue(coordinator.ensureSignedIn().exceptionOrNull() is CloudSyncException.Offline)
        }
        assertEquals(2, tokens.silentCalls)
    }

    @Test
    fun `interactive token clears required state and throttle`() = runTest {
        val coordinator = coordinator(
            FakeBackend(),
            FakeTokens(silent = Result.failure(IllegalStateException("Consent required"))),
        )
        coordinator.ensureSignedIn()
        assertTrue(coordinator.signInWithGoogleIdToken("confirmed-token").isSuccess)
        assertEquals(CloudAccessState.CONNECTED, coordinator.accessState.value)
        assertTrue(coordinator.ensureSignedIn().isSuccess)
    }

    @Test
    fun `cancellation escapes withSession`() = runTest {
        val coordinator = coordinator(FakeBackend(authenticated()))
        try {
            coordinator.withSession<Unit> { throw CancellationException("cancel") }
            throw AssertionError("Cancellation was swallowed")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun `invalid JWT payload and expiry boundary are rejected`() {
        assertFalse(googleIdTokenIsFresh("not-a-jwt", NOW))
        assertFalse(googleIdTokenIsFresh("a.invalid.c", NOW))
        assertFalse(googleIdTokenIsFresh(jwt(NOW / 1_000 + 60), NOW + 60_000))
    }

    private fun TestScope.coordinator(backend: FakeBackend, tokens: FakeTokens = FakeTokens()) =
        SupabaseSessionCoordinator(
            backend, tokens, backgroundScope,
            nowMillis = { NOW + testScheduler.currentTime },
        )

    private class FakeTokens(
        val stored: String? = null,
        val silent: Result<String> = Result.success("fresh-token"),
    ) : GoogleIdTokenProvider {
        var silentCalls = 0
        override fun lastGoogleIdToken(): String? = stored
        override suspend fun freshGoogleIdTokenSilently(): Result<String> {
            silentCalls++
            return silent
        }
    }

    private class FakeBackend(initial: SupabaseAuthStatus = SupabaseAuthStatus.NotAuthenticated) : SupabaseAuthBackend {
        val states = MutableStateFlow(initial)
        override val status get() = states.value
        override val statusChanges = states
        var refreshes = 0
        var signIns = 0
        var clears = 0
        var signInDelayMillis = 0L
        var refreshError: Throwable? = null
        var signInError: Throwable? = null
        val usedTokens = mutableListOf<String>()
        override suspend fun awaitInitialization() {
            states.first { it != SupabaseAuthStatus.Initializing }
        }
        override suspend fun refreshCurrentSession() {
            refreshes++
            refreshError?.let { throw it }
            states.value = authenticated()
        }
        override suspend fun signInWithIdToken(idToken: String) {
            signIns++
            usedTokens += idToken
            delay(signInDelayMillis)
            signInError?.let { throw it }
            states.value = authenticated()
        }
        override suspend fun clearSession() {
            clears++
            states.value = SupabaseAuthStatus.NotAuthenticated
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        fun authenticated() = SupabaseAuthStatus.Authenticated(NOW + 3_600_000)
        fun jwt(exp: Long): String = "header." +
            Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":$exp}".toByteArray()) + ".signature"
    }
}
