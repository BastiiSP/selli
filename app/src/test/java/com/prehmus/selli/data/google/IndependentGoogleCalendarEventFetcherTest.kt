package com.prehmus.selli.data.google

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.model.CalendarAuthRequiredException
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndependentGoogleCalendarEventFetcherTest {

    @Test
    fun fetch_partnerFailureKeepsOwnEventsAndLogsFailure() = runTest {
        val ownEvent = event("own")
        val failure = GoogleJsonResponseException(
            HttpResponseException.Builder(404, "Not Found", HttpHeaders()),
            null,
        )
        val logger = FakeCalendarLogger()
        val fetcher = IndependentGoogleCalendarEventFetcher(logger)

        val result = fetcher.fetch(
            fetchOwn = { listOf(ownEvent) },
            fetchPartner = { throw failure },
        )

        assertEquals(listOf(ownEvent), result)
        assertEquals("Google partner calendar", logger.source)
        assertTrue(logger.cause is GoogleJsonResponseException)
        assertEquals(404, (logger.cause as GoogleJsonResponseException).statusCode)
    }

    @Test
    fun fetch_ownAuthExceptionPropagates() = runTest {
        val fetcher = IndependentGoogleCalendarEventFetcher(FakeCalendarLogger())

        val thrown = try {
            fetcher.fetch(
                fetchOwn = { throw CalendarAuthRequiredException("Consent needed") },
                fetchPartner = { emptyList() },
            )
            null
        } catch (error: CalendarAuthRequiredException) {
            error
        }

        assertTrue(thrown is CalendarAuthRequiredException)
        assertEquals("Consent needed", thrown?.message)
    }

    private fun event(id: String): CalendarEvent = CalendarEvent(
        id = id,
        title = id,
        start = LocalDateTime.of(2026, 7, 18, 10, 0),
        end = LocalDateTime.of(2026, 7, 18, 11, 0),
        isAllDay = false,
        source = CalendarSource.GOOGLE_OWN,
        owner = Person.BASTI,
        isSharedEvent = false,
    )

    private class FakeCalendarLogger : CalendarLogger {
        var source: String? = null
        var cause: Throwable? = null

        override fun error(source: String, cause: Throwable) {
            this.source = source
            this.cause = cause
        }
    }
}
