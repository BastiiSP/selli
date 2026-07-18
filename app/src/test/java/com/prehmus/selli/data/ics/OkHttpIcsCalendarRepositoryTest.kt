package com.prehmus.selli.data.ics

import com.prehmus.selli.domain.model.DateRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpIcsCalendarRepositoryTest {
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
}
