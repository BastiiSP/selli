package com.prehmus.selli.data.places

import com.google.gson.JsonParser
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.places.LocationSuggestion
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesAutocompleteRepositoryTest {
    @Test
    fun `suggest maps successful response and skips unusable predictions`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(successResponse),
        )
        server.start()
        try {
            val repository = repository(server)

            val result = repository.suggest("Musterstraße")

            assertEquals(
                listOf(
                    LocationSuggestion(
                        placeId = "place-musterstrasse",
                        primaryText = "Musterstraße 1",
                        secondaryText = "12345 Berlin, Deutschland",
                        fullText = "Musterstraße 1, 12345 Berlin, Deutschland",
                    ),
                    LocationSuggestion(
                        placeId = "",
                        primaryText = "Berlin, Deutschland",
                        secondaryText = null,
                        fullText = "Berlin, Deutschland",
                    ),
                ),
                result,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `suggest sends API key and JSON request body in a POST request`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"suggestions\":[]}"))
        server.start()
        try {
            val repository = repository(server)

            repository.suggest("  Bäcker \"Ecke\"  ")

            val request = server.takeRequest()
            val requestJson = JsonParser.parseString(request.decodedBody()).asJsonObject
            assertEquals("POST", request.method)
            assertEquals(TEST_API_KEY, request.getHeader("X-Goog-Api-Key"))
            assertEquals("application/json", request.getHeader("Content-Type"))
            assertEquals("Bäcker \"Ecke\"", requestJson.get("input").asString)
            assertEquals("de", requestJson.get("languageCode").asString)
            assertEquals("DE", requestJson.get("regionCode").asString)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `suggest returns empty list and logs HTTP error`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.start()
        try {
            val logger = FakeCalendarLogger()
            val repository = repository(server, logger)

            val result = repository.suggest("Musterstraße")

            assertTrue(result.isEmpty())
            assertEquals(1, logger.errors.size)
            assertTrue(logger.errors.single().cause.message.orEmpty().contains("HTTP 403"))
            assertTrue(logger.errors.single().cause.message.orEmpty().contains(TEST_API_KEY).not())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `suggest returns empty list and logs network error`() = runTest {
        val server = MockWebServer()
        server.start()
        val endpoint = server.url("/v1/places:autocomplete")
        server.shutdown()
        val logger = FakeCalendarLogger()
        val repository = PlacesAutocompleteRepository(
            apiKey = TEST_API_KEY,
            callFactory = OkHttpClient(),
            logger = logger,
            endpoint = endpoint,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = repository.suggest("Musterstraße")

        assertTrue(result.isEmpty())
        assertEquals(1, logger.errors.size)
    }

    @Test
    fun `suggest propagates cancellation without logging it`() = runTest {
        val logger = FakeCalendarLogger()
        val callFactory = OkHttpClient.Builder()
            .addInterceptor {
                throw CancellationException("cancelled")
            }
            .build()
        val repository = PlacesAutocompleteRepository(
            apiKey = TEST_API_KEY,
            callFactory = callFactory,
            logger = logger,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val thrown = try {
            repository.suggest("Musterstraße")
            null
        } catch (exception: CancellationException) {
            exception
        }

        assertEquals("cancelled", thrown?.message)
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun `suggest avoids network for short input and empty API key`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val configuredRepository = repository(server)
            val unconfiguredRepository = PlacesAutocompleteRepository(
                apiKey = "",
                callFactory = OkHttpClient(),
                logger = FakeCalendarLogger(),
                endpoint = server.url("/v1/places:autocomplete"),
                ioDispatcher = Dispatchers.Unconfined,
            )

            assertTrue(configuredRepository.suggest("  ab  ").isEmpty())
            assertTrue(unconfiguredRepository.suggest("Musterstraße").isEmpty())
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `suggest returns empty list for malformed and unexpected JSON`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{not-json"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"suggestions\":[{\"placePrediction\":\"invalid\"}]}"),
        )
        server.start()
        try {
            val logger = FakeCalendarLogger()
            val repository = repository(server, logger)

            assertTrue(repository.suggest("Musterstraße").isEmpty())
            assertTrue(repository.suggest("Musterstraße").isEmpty())
            assertEquals(2, logger.errors.size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `suggest returns at most five predictions`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sixSuggestionsResponse),
        )
        server.start()
        try {
            val repository = repository(server)

            val result = repository.suggest("Musterstraße")

            assertEquals(
                listOf("Ort 1", "Ort 2", "Ort 3", "Ort 4", "Ort 5"),
                result.map(LocationSuggestion::fullText),
            )
        } finally {
            server.shutdown()
        }
    }

    // --- Android-Identität: ohne diese Header weist Google den beschränkten Schlüssel ab ---

    @Test
    fun `requests carry the Android identity headers only when both values are known`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"suggestions\":[]}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"suggestions\":[]}"))
        server.start()
        try {
            repository(server, packageName = TEST_PACKAGE, certSha1 = TEST_CERT)
                .suggest("Musterstraße")
            val identified = server.takeRequest()
            assertEquals(TEST_PACKAGE, identified.getHeader("X-Android-Package"))
            assertEquals(TEST_CERT, identified.getHeader("X-Android-Cert"))

            repository(server).suggest("Musterstraße")
            val anonymous = server.takeRequest()
            assertNull(anonymous.getHeader("X-Android-Package"))
            assertNull(anonymous.getHeader("X-Android-Cert"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `details request carries the Android identity headers`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"formattedAddress\":\"Irgendwo 1\"}"),
        )
        server.start()
        try {
            repository(server, packageName = TEST_PACKAGE, certSha1 = TEST_CERT)
                .resolveFullAddress("place-1")

            val request = server.takeRequest()
            assertEquals(TEST_PACKAGE, request.getHeader("X-Android-Package"))
            assertEquals(TEST_CERT, request.getHeader("X-Android-Cert"))
        } finally {
            server.shutdown()
        }
    }

    // --- Sitzungs-Token klammert Vorschläge und Detailabfrage zu einem Vorgang ---

    @Test
    fun `session token travels in the autocomplete body and the details query`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"suggestions\":[]}"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"formattedAddress\":\"Irgendwo 1\"}"),
        )
        server.start()
        try {
            val repository = repository(server)

            repository.suggest("Musterstraße", sessionToken = "token-1")
            val autocomplete = server.takeRequest()
            val autocompleteJson = JsonParser.parseString(autocomplete.decodedBody()).asJsonObject
            assertEquals("token-1", autocompleteJson.get("sessionToken").asString)

            repository.resolveFullAddress("place-1", sessionToken = "token-1")
            val details = server.takeRequest()
            assertEquals("token-1", details.requestUrl?.queryParameter("sessionToken"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `without a session token neither request mentions one`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"suggestions\":[]}"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"formattedAddress\":\"Irgendwo 1\"}"),
        )
        server.start()
        try {
            val repository = repository(server)

            repository.suggest("Musterstraße")
            val autocompleteJson =
                JsonParser.parseString(server.takeRequest().decodedBody()).asJsonObject
            assertTrue(autocompleteJson.has("sessionToken").not())

            repository.resolveFullAddress("place-1")
            assertNull(server.takeRequest().requestUrl?.queryParameter("sessionToken"))
        } finally {
            server.shutdown()
        }
    }

    // --- Vollständige Anschrift zum ausgewählten Vorschlag ---

    @Test
    fun `resolveFullAddress returns the formatted address and asks for just that field`() =
        runTest {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        "{\"formattedAddress\":" +
                            "\"Marienplatz 1, 80331 München, Deutschland\"}",
                    ),
            )
            server.start()
            try {
                val result = repository(server).resolveFullAddress("place-marienplatz")

                assertEquals("Marienplatz 1, 80331 München, Deutschland", result)
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("formattedAddress", request.getHeader("X-Goog-FieldMask"))
                assertEquals(
                    "place-marienplatz",
                    request.requestUrl?.pathSegments?.last(),
                )
                assertEquals("de", request.requestUrl?.queryParameter("languageCode"))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `resolveFullAddress returns null for HTTP errors, blank bodies and malformed JSON`() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{not-json"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"formattedAddress\":\"  \"}"))
            server.start()
            try {
                val logger = FakeCalendarLogger()
                val repository = repository(server, logger)

                assertNull(repository.resolveFullAddress("place-1"))
                assertNull(repository.resolveFullAddress("place-1"))
                assertNull(repository.resolveFullAddress("place-1"))
                // Nur HTTP-Fehler und kaputtes JSON sind echte Fehler; ein leeres Feld nicht.
                assertEquals(2, logger.errors.size)
                assertTrue(logger.errors.none { it.cause.message.orEmpty().contains(TEST_API_KEY) })
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `resolveFullAddress returns null on network errors`() = runTest {
        val server = MockWebServer()
        server.start()
        val detailsEndpoint = server.url("/v1/places")
        server.shutdown()
        val logger = FakeCalendarLogger()
        val repository = PlacesAutocompleteRepository(
            apiKey = TEST_API_KEY,
            callFactory = OkHttpClient(),
            logger = logger,
            detailsEndpoint = detailsEndpoint,
            ioDispatcher = Dispatchers.Unconfined,
        )

        assertNull(repository.resolveFullAddress("place-1"))
        assertEquals(1, logger.errors.size)
    }

    @Test
    fun `resolveFullAddress avoids network for blank place id and empty API key`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val configuredRepository = repository(server)
            val unconfiguredRepository = PlacesAutocompleteRepository(
                apiKey = "",
                callFactory = OkHttpClient(),
                logger = FakeCalendarLogger(),
                detailsEndpoint = server.url("/v1/places"),
                ioDispatcher = Dispatchers.Unconfined,
            )

            assertNull(configuredRepository.resolveFullAddress("   "))
            assertNull(unconfiguredRepository.resolveFullAddress("place-1"))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun repository(
        server: MockWebServer,
        logger: CalendarLogger = FakeCalendarLogger(),
        packageName: String? = null,
        certSha1: String? = null,
    ): PlacesAutocompleteRepository = PlacesAutocompleteRepository(
        apiKey = TEST_API_KEY,
        androidPackageName = packageName,
        androidCertSha1 = certSha1,
        callFactory = OkHttpClient(),
        logger = logger,
        endpoint = server.url("/v1/places:autocomplete"),
        detailsEndpoint = server.url("/v1/places"),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun RecordedRequest.decodedBody(): String {
        val bytes = body.readByteArray()
        if (!getHeader("Content-Encoding").equals("gzip", ignoreCase = true)) {
            return bytes.toString(Charsets.UTF_8)
        }

        return GZIPInputStream(ByteArrayInputStream(bytes))
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }
    }

    private class FakeCalendarLogger : CalendarLogger {
        val errors = mutableListOf<LoggedError>()

        override fun error(source: String, cause: Throwable) {
            errors += LoggedError(source, cause)
        }

        data class LoggedError(
            val source: String,
            val cause: Throwable,
        )
    }

    private companion object {
        const val TEST_API_KEY = "dummy-places-key"
        const val TEST_PACKAGE = "com.prehmus.selli"
        const val TEST_CERT = "0123456789ABCDEF0123456789ABCDEF01234567"

        val successResponse =
            """
            {
              "suggestions": [
                {
                  "placePrediction": {
                    "placeId": "place-musterstrasse",
                    "text": {"text": "Musterstraße 1, 12345 Berlin, Deutschland"},
                    "structuredFormat": {
                      "mainText": {"text": "Musterstraße 1"},
                      "secondaryText": {"text": "12345 Berlin, Deutschland"}
                    }
                  }
                },
                {
                  "placePrediction": {
                    "text": {"text": "Berlin, Deutschland"}
                  }
                },
                {
                  "placePrediction": {
                    "text": {"text": "   "},
                    "structuredFormat": {
                      "mainText": {"text": "Unbrauchbar"}
                    }
                  }
                }
              ]
            }
            """.trimIndent()

        val sixSuggestionsResponse =
            """
            {
              "suggestions": [
                {"placePrediction":{"text":{"text":"Ort 1"}}},
                {"placePrediction":{"text":{"text":"Ort 2"}}},
                {"placePrediction":{"text":{"text":"Ort 3"}}},
                {"placePrediction":{"text":{"text":"Ort 4"}}},
                {"placePrediction":{"text":{"text":"Ort 5"}}},
                {"placePrediction":{"text":{"text":"Ort 6"}}}
              ]
            }
            """.trimIndent()
    }
}
