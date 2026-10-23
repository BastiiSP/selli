package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.GoogleReconfirmation
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnGoogleAccountTokensTest {
    @Test
    fun `silent fetch uses saved own account and only persists token`() = runTest {
        var requestedEmail: String? = null
        var persistedToken: String? = null
        val tokens = OwnGoogleAccountTokens(
            ownEmail = { "own@example.test" },
            persistToken = { persistedToken = it },
            silentSource = { email -> requestedEmail = email; "fresh-token" },
        )
        assertEquals("fresh-token", tokens.freshSilently().getOrThrow())
        assertEquals("own@example.test", requestedEmail)
        assertEquals("fresh-token", persistedToken)
    }

    @Test
    fun `wrong account changes nothing`() {
        var persistedToken: String? = null
        val tokens = OwnGoogleAccountTokens({ "own@example.test" }, { persistedToken = it }, { "token" })
        assertEquals(
            GoogleReconfirmation.WrongAccount("own@example.test"),
            tokens.acceptReconfirmation("own@example.test", "other@example.test", "wrong-token"),
        )
        assertNull(persistedToken)
    }

    @Test
    fun `same account is compared case insensitively`() {
        var persistedToken: String? = null
        val tokens = OwnGoogleAccountTokens({ "Own@example.test" }, { persistedToken = it }, { "token" })
        assertEquals(
            GoogleReconfirmation.Success("confirmed"),
            tokens.acceptReconfirmation("Own@example.test", "OWN@EXAMPLE.TEST", "confirmed"),
        )
        assertEquals("confirmed", persistedToken)
    }

    @Test
    fun `missing own account does not fetch a token`() = runTest {
        val tokens = OwnGoogleAccountTokens({ null }, { error("must not persist") }, { error("must not fetch") })
        assertTrue(tokens.freshSilently().exceptionOrNull() is CloudSyncException.ReconfirmationRequired)
    }

    @Test
    fun `network errors and recoverable auth errors are distinct`() = runTest {
        for (error in listOf(IOException("offline"), IllegalStateException("consent required"))) {
            val tokens = OwnGoogleAccountTokens({ "own@example.test" }, { error("must not persist") }, { throw error })
            val result = tokens.freshSilently().exceptionOrNull()
            if (error is IOException) assertTrue(result is CloudSyncException.Offline)
            else assertTrue(result is CloudSyncException.ReconfirmationRequired)
        }
    }

    @Test
    fun `account changing during silent fetch cannot persist a stale token`() = runTest {
        var email = "own@example.test"
        val tokens = OwnGoogleAccountTokens(
            { email },
            { error("must not persist") },
            { email = "other@example.test"; "stale-token" },
        )
        assertTrue(tokens.freshSilently().exceptionOrNull() is CloudSyncException.ReconfirmationRequired)
    }

    @Test(expected = CancellationException::class)
    fun `silent cancellation propagates`() = runTest {
        OwnGoogleAccountTokens(
            { "own@example.test" },
            {},
            { throw CancellationException("cancel") },
        ).freshSilently()
        Unit
    }
}
