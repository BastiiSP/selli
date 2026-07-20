package com.prehmus.selli.data.ics

import com.prehmus.selli.domain.model.DateRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpIcsCalendarRepositoryTest {
    @Test
    fun `fetchEvents reuses downloaded feed within TTL across different ranges`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(emptyCalendar))
        server.start()
        try {
            var currentTime = 1_000L
            val repository = repository(server = server, now = { currentTime })

            repository.fetchEvents(testRange)
            repository.fetchEvents(
                DateRange(
                    start = LocalDate.of(2026, 2, 1),
                    endInclusive = LocalDate.of(2026, 2, 28),
                ),
            )

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchEvents force refresh downloads feed again within TTL`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(emptyCalendar))
        server.enqueue(MockResponse().setResponseCode(200).setBody(emptyCalendar))
        server.start()
        try {
            val repository = repository(server = server)

            repository.fetchEvents(testRange)
            repository.fetchEvents(testRange, forceRefresh = true)

            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchEvents downloads feed again after TTL`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(emptyCalendar))
        server.enqueue(MockResponse().setResponseCode(200).setBody(emptyCalendar))
        server.start()
        try {
            var currentTime = 1_000L
            val repository = repository(server = server, now = { currentTime })

            repository.fetchEvents(testRange)
            currentTime += 15 * 60 * 1_000L
            repository.fetchEvents(testRange)

            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchEvents downloads feed and returns parsed events`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    BEGIN:VCALENDAR
                    BEGIN:VEVENT
                    UID:http-1
                    DTSTART:20260111T090000
                    DTEND:20260111T100000
                    SUMMARY:Fetched event
                    END:VEVENT
                    END:VCALENDAR
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val repository = OkHttpIcsCalendarRepository(
                feedUrl = server.url("/calendar.ics").toString(),
                client = OkHttpClient(),
                parser = IcsCalendarParser(systemZone = ZoneId.of("Europe/Berlin")),
            )

            val events = repository.fetchEvents(
                DateRange(
                    start = LocalDate.of(2026, 1, 1),
                    endInclusive = LocalDate.of(2026, 1, 31),
                ),
            )

            assertEquals("GET", server.takeRequest().method)
            assertEquals(listOf("Fetched event"), events.map { it.title })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchEvents retries transient HTTP failure and succeeds on second attempt`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody(emptyCalendar))
        server.start()
        try {
            val backoffAttempts = mutableListOf<Int>()
            val repository = repository(
                server = server,
                retryBackoff = { attempt -> backoffAttempts += attempt },
            )

            assertTrue(repository.fetchEvents(testRange).isEmpty())
            assertEquals(2, server.requestCount)
            assertEquals(listOf(1), backoffAttempts)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchEvents does not retry non-transient HTTP 4xx`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(400))
        server.start()
        try {
            val backoffAttempts = mutableListOf<Int>()
            val repository = repository(
                server = server,
                retryBackoff = { attempt -> backoffAttempts += attempt },
            )

            val thrown = try {
                repository.fetchEvents(testRange)
                null
            } catch (error: Exception) {
                error
            }

            assertTrue(thrown is java.io.IOException)
            assertEquals("Failed to fetch ICS feed: HTTP 400", thrown?.message)
            assertEquals(1, server.requestCount)
            assertTrue(backoffAttempts.isEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchEvents does not catch or retry cancellation`() = runTest {
        val backoffAttempts = mutableListOf<Int>()
        val client = OkHttpClient.Builder()
            .addInterceptor {
                throw CancellationException("cancelled")
            }
            .build()
        val repository = OkHttpIcsCalendarRepository(
            feedUrl = "https://example.test/calendar.ics",
            client = client,
            retryBackoff = { attempt -> backoffAttempts += attempt },
        )

        val thrown = try {
            repository.fetchEvents(testRange)
            null
        } catch (error: CancellationException) {
            error
        }

        assertTrue(thrown is CancellationException)
        assertEquals("cancelled", thrown?.message)
        assertTrue(backoffAttempts.isEmpty())
    }

    private fun repository(
        server: MockWebServer,
        retryBackoff: suspend (attempt: Int) -> Unit = {},
        now: () -> Long = { 1_000L },
    ): OkHttpIcsCalendarRepository = OkHttpIcsCalendarRepository(
        feedUrl = server.url("/calendar.ics").toString(),
        client = OkHttpClient(),
        parser = IcsCalendarParser(systemZone = ZoneId.of("Europe/Berlin")),
        retryBackoff = retryBackoff,
        now = now,
    )

    private companion object {
        const val emptyCalendar = "BEGIN:VCALENDAR\r\nEND:VCALENDAR"
        val testRange = DateRange(
            start = LocalDate.of(2026, 1, 1),
            endInclusive = LocalDate.of(2026, 1, 31),
        )
    }
}
