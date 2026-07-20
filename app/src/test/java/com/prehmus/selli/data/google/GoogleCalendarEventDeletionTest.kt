package com.prehmus.selli.data.google

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DeletionScope
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarEventDeletionTest {
    private val zoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun `single own event deletes its event id`() = withServer { server, deletion ->
        server.enqueue(MockResponse().setResponseCode(204))

        val result = deletion.delete(ownEvent(id = "event-1"), DeletionScope.SINGLE_OCCURRENCE)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/calendar/v3/calendars/primary/events/event-1", request.path)
    }

    @Test
    fun `single recurring occurrence deletes instance id`() = withServer { server, deletion ->
        server.enqueue(MockResponse().setResponseCode(204))

        deletion.delete(
            ownEvent(id = "instance-1", seriesId = "series-1"),
            DeletionScope.SINGLE_OCCURRENCE,
        )

        assertEquals("/calendar/v3/calendars/primary/events/instance-1", server.takeRequest().path)
    }

    @Test
    fun `this and following updates master rrule with utc until and without count`() =
        withServer { server, deletion ->
            server.enqueue(
                jsonResponse(
                    """{"id":"series-1","start":{"dateTime":"2026-07-01T10:00:00+02:00"},"recurrence":["RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;COUNT=12"]}""",
                ),
            )
            server.enqueue(jsonResponse("{}"))

            val result = deletion.delete(
                ownEvent(
                    id = "instance-3",
                    seriesId = "series-1",
                    start = LocalDateTime.of(2026, 7, 20, 10, 0),
                ),
                DeletionScope.THIS_AND_FOLLOWING,
            )

            assertTrue(result.isSuccess)
            assertEquals("GET", server.takeRequest().method)
            val update = server.takeRequest()
            assertEquals("PUT", update.method)
            assertEquals("/calendar/v3/calendars/primary/events/series-1", update.path)
            val body = update.decodedBody()
            assertTrue(body.contains("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;UNTIL=20260720T075959Z"))
            assertFalse(body.contains("COUNT"))
        }

    @Test
    fun `this and following at series start deletes master`() = withServer { server, deletion ->
        server.enqueue(
            jsonResponse(
                """{"id":"series-1","start":{"date":"2026-07-20"},"recurrence":["RRULE:FREQ=DAILY"]}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        deletion.delete(
            ownEvent(
                id = "instance-1",
                seriesId = "series-1",
                start = LocalDateTime.of(2026, 7, 20, 0, 0),
                isAllDay = true,
            ),
            DeletionScope.THIS_AND_FOLLOWING,
        )

        server.takeRequest()
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/calendar/v3/calendars/primary/events/series-1", delete.path)
    }

    @Test
    fun `non-own sources fail without api call`() = withServer { server, deletion ->
        listOf(CalendarSource.WORK_ICS, CalendarSource.GOOGLE_PARTNER).forEach { source ->
            val result = deletion.delete(ownEvent(source = source), DeletionScope.SINGLE_OCCURRENCE)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `already deleted event is successful for not found and gone`() = withServer { server, deletion ->
        server.enqueue(jsonResponse(errorBody(404)).setResponseCode(404))
        server.enqueue(jsonResponse(errorBody(410)).setResponseCode(410))

        assertTrue(deletion.delete(ownEvent(id = "missing"), DeletionScope.SINGLE_OCCURRENCE).isSuccess)
        assertTrue(deletion.delete(ownEvent(id = "gone"), DeletionScope.SINGLE_OCCURRENCE).isSuccess)
        assertEquals(2, server.requestCount)
    }

    private fun withServer(
        block: suspend (MockWebServer, GoogleCalendarEventDeletion) -> Unit,
    ) = runTest {
        MockWebServer().use { server ->
            server.start()
            val calendar = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), null)
                .setRootUrl(server.url("/").toString())
                .setApplicationName("test")
                .build()
            block(server, GoogleCalendarEventDeletion(calendar, zoneId))
        }
    }

    private fun ownEvent(
        id: String = "event-1",
        seriesId: String? = null,
        start: LocalDateTime = LocalDateTime.of(2026, 7, 20, 10, 0),
        isAllDay: Boolean = false,
        source: CalendarSource = CalendarSource.GOOGLE_OWN,
    ) = CalendarEvent(
        id = id,
        title = "Termin",
        start = start,
        end = start.plusHours(1),
        isAllDay = isAllDay,
        source = source,
        owner = Person.BASTI,
        isSharedEvent = false,
        seriesId = seriesId,
    )

    private fun errorBody(code: Int) =
        """{"error":{"code":$code,"message":"deleted","errors":[]}}"""

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(body)

    private fun RecordedRequest.decodedBody(): String =
        if (getHeader("Content-Encoding").equals("gzip", ignoreCase = true)) {
            GZIPInputStream(body.inputStream()).bufferedReader().use { it.readText() }
        } else {
            body.readUtf8()
        }
}
