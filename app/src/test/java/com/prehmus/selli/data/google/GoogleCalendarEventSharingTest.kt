package com.prehmus.selli.data.google

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarEventSharingTest {
    @Test
    fun `sharing occurrence adds partner and preserves other attendees`() =
        withServer { server, sharing ->
            server.enqueue(
                jsonResponse(
                    """{"id":"event-1","attendees":[{"email":"friend@example.com"}],"extendedProperties":{"shared":{"other":"keep"}}}""",
                ),
            )
            server.enqueue(jsonResponse("{}"))

            val result = sharing.setPartnerAttendance(
                event = ownEvent(),
                partnerEmail = "melli@example.com",
                shared = true,
                wholeSeries = false,
            )

            assertTrue(result.isSuccess)
            assertEquals("/calendar/v3/calendars/primary/events/event-1", server.takeRequest().path)
            val update = server.takeRequest()
            assertEquals("PUT", update.method)
            assertEquals("all", update.requestUrl?.queryParameter("sendUpdates"))
            val body = update.decodedBody()
            assertTrue(body.contains("friend@example.com"))
            assertTrue(body.contains("melli@example.com"))
            assertTrue(body.contains("\"selli:shared\":\"true\""))
            assertTrue(body.contains("\"other\":\"keep\""))
        }

    @Test
    fun `unsharing removes only partner and clears selli marker`() =
        withServer { server, sharing ->
            server.enqueue(
                jsonResponse(
                    """{"id":"event-1","attendees":[{"email":"melli@example.com"},{"email":"friend@example.com"}],"extendedProperties":{"shared":{"selli:shared":"true","other":"keep"}}}""",
                ),
            )
            server.enqueue(jsonResponse("{}"))

            val result = sharing.setPartnerAttendance(
                event = ownEvent(isShared = true),
                partnerEmail = "MELLI@example.com",
                shared = false,
                wholeSeries = false,
            )

            assertTrue(result.isSuccess)
            server.takeRequest()
            val body = server.takeRequest().decodedBody()
            assertFalse(body.contains("melli@example.com", ignoreCase = true))
            assertTrue(body.contains("friend@example.com"))
            assertFalse(body.contains("selli:shared"))
            assertTrue(body.contains("\"other\":\"keep\""))
        }

    @Test
    fun `whole series sharing updates recurring master`() =
        withServer { server, sharing ->
            server.enqueue(jsonResponse("""{"id":"series-1"}"""))
            server.enqueue(jsonResponse("{}"))

            val result = sharing.setPartnerAttendance(
                event = ownEvent(seriesId = "series-1"),
                partnerEmail = "melli@example.com",
                shared = true,
                wholeSeries = true,
            )

            assertTrue(result.isSuccess)
            assertEquals("/calendar/v3/calendars/primary/events/series-1", server.takeRequest().path)
            assertEquals("/calendar/v3/calendars/primary/events/series-1", server.takeRequest().requestUrl?.encodedPath)
        }

    @Test
    fun `single series occurrence sharing updates instance`() =
        withServer { server, sharing ->
            server.enqueue(jsonResponse("""{"id":"instance-1"}"""))
            server.enqueue(jsonResponse("{}"))

            sharing.setPartnerAttendance(
                event = ownEvent(id = "instance-1", seriesId = "series-1"),
                partnerEmail = "melli@example.com",
                shared = true,
                wholeSeries = false,
            )

            assertEquals("/calendar/v3/calendars/primary/events/instance-1", server.takeRequest().path)
        }

    @Test
    fun `non-own event fails without api call`() =
        withServer { server, sharing ->
            val result = sharing.setPartnerAttendance(
                event = ownEvent(source = CalendarSource.GOOGLE_PARTNER),
                partnerEmail = "melli@example.com",
                shared = true,
                wholeSeries = false,
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `deletion request for own event fails without api call`() =
        withServer { server, sharing ->
            val result = sharing.requestDeletion(
                event = ownEvent(category = EventCategory.TOGETHER),
                requestedBy = Person.BASTI,
                wholeSeries = false,
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `deletion request for non-together event fails without api call`() =
        withServer { server, sharing ->
            val result = sharing.requestDeletion(
                event = ownEvent(
                    source = CalendarSource.GOOGLE_PARTNER,
                    category = EventCategory.PRIVATE,
                ),
                requestedBy = Person.BASTI,
                wholeSeries = false,
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `deletion request marks partner occurrence and preserves other shared properties`() =
        withServer { server, sharing ->
            server.enqueue(
                jsonResponse(
                    """{"id":"event-1","extendedProperties":{"shared":{"other":"keep"}}}""",
                ),
            )
            server.enqueue(jsonResponse("{}"))

            val result = sharing.requestDeletion(
                event = ownEvent(
                    source = CalendarSource.GOOGLE_PARTNER,
                    category = EventCategory.TOGETHER,
                ),
                requestedBy = Person.BASTI,
                wholeSeries = false,
            )

            assertTrue(result.isSuccess)
            assertEquals("/calendar/v3/calendars/primary/events/event-1", server.takeRequest().path)
            val update = server.takeRequest()
            assertEquals("PUT", update.method)
            assertEquals("none", update.requestUrl?.queryParameter("sendUpdates"))
            val body = update.decodedBody()
            assertTrue(body.contains("\"selli:deleteRequestedBy\":\"BASTI\""))
            assertTrue(body.contains("\"other\":\"keep\""))
        }

    @Test
    fun `whole series deletion request updates recurring master`() =
        withServer { server, sharing ->
            server.enqueue(jsonResponse("""{"id":"series-1"}"""))
            server.enqueue(jsonResponse("{}"))

            val result = sharing.requestDeletion(
                event = ownEvent(
                    id = "instance-1",
                    seriesId = "series-1",
                    source = CalendarSource.GOOGLE_PARTNER,
                    category = EventCategory.TOGETHER,
                ),
                requestedBy = Person.BASTI,
                wholeSeries = true,
            )

            assertTrue(result.isSuccess)
            assertEquals("/calendar/v3/calendars/primary/events/series-1", server.takeRequest().path)
            assertEquals(
                "/calendar/v3/calendars/primary/events/series-1",
                server.takeRequest().requestUrl?.encodedPath,
            )
        }

    @Test
    fun `forbidden deletion request returns failure`() =
        withServer { server, sharing ->
            server.enqueue(
                jsonResponse(
                    """{"error":{"code":403,"message":"Forbidden"}}""",
                ).setResponseCode(403),
            )

            val result = sharing.requestDeletion(
                event = ownEvent(
                    source = CalendarSource.GOOGLE_PARTNER,
                    category = EventCategory.TOGETHER,
                ),
                requestedBy = Person.BASTI,
                wholeSeries = false,
            )

            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        }

    private fun withServer(
        block: suspend (MockWebServer, GoogleCalendarEventSharing) -> Unit,
    ) = runTest {
        MockWebServer().use { server ->
            server.start()
            val calendar = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), null)
                .setRootUrl(server.url("/").toString())
                .setApplicationName("test")
                .build()
            block(server, GoogleCalendarEventSharing(calendar))
        }
    }

    private fun ownEvent(
        id: String = "event-1",
        seriesId: String? = null,
        isShared: Boolean = false,
        source: CalendarSource = CalendarSource.GOOGLE_OWN,
        category: EventCategory = EventCategory.PRIVATE,
    ) = CalendarEvent(
        id = id,
        title = "Termin",
        start = LocalDateTime.of(2026, 8, 3, 18, 0),
        end = LocalDateTime.of(2026, 8, 3, 19, 0),
        isAllDay = false,
        source = source,
        owner = Person.BASTI,
        isSharedEvent = isShared,
        seriesId = seriesId,
        category = category,
    )

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
