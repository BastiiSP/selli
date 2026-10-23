package com.prehmus.selli.data.supabase

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Echte SDK-Exception samt Response, nur Loopback – kein Supabase-Projekt, kein Android nötig. */
internal fun cloudHttpError(
    status: Int,
    message: String,
    path: String = "/rest/v1/locations",
): RestException = MockWebServer().use { server ->
    server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
    server.start()
    runBlocking {
        val client = HttpClient(OkHttp) { expectSuccess = false }
        try {
            RestException(message, null, client.get(server.url(path).toString()))
        } finally {
            client.close()
        }
    }
}
