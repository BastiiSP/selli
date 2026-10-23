package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.model.CloudSyncException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudErrorsTest {
    @Test
    fun `network and timeout failures are offline`() {
        val errors = listOf(
            IOException("offline"),
            SocketTimeoutException("timeout"),
            HttpRequestException("network", HttpRequestBuilder()),
            HttpRequestTimeoutException(HttpRequestBuilder()),
        )
        errors.forEach { assertTrue(classifyCloudError(it) is CloudSyncException.Offline) }
    }

    @Test
    fun `bad Google ID token needs reconfirmation`() {
        assertTrue(
            classifyCloudError(cloudHttpError(400, "Bad ID token", "/auth/v1/token")) is
                CloudSyncException.ReconfirmationRequired,
        )
    }

    @Test
    fun `500 is Failed and does not request a new account`() {
        assertTrue(classifyCloudError(cloudHttpError(500, "Internal server error")) is CloudSyncException.Failed)
    }

    @Test
    fun `RLS rejection and malformed data are not an authentication loss`() {
        assertTrue(classifyCloudError(cloudHttpError(403, "row-level security")) is CloudSyncException.Failed)
        assertTrue(classifyCloudError(cloudHttpError(400, "invalid input syntax")) is CloudSyncException.Failed)
        assertTrue(classifyCloudError(IllegalArgumentException("decode failed")) is CloudSyncException.Failed)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never wrapped`() {
        classifyCloudError(CancellationException("cancel"))
    }

    @Test
    fun `logging removes original response headers and cause`() {
        var logged: Throwable? = null
        logCloudError(
            CalendarLogger { _, error -> logged = error },
            "test",
            CloudSyncException.Failed(IOException("secret-token")),
        )
        assertTrue(logged is CloudSyncException.Failed)
        assertNull(logged!!.cause)
        assertTrue(!logged!!.toString().contains("secret-token"))
    }
}
