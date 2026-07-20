package com.prehmus.selli.data.ics

import com.prehmus.selli.BuildConfig
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpIcsCalendarRepository(
    private val feedUrl: String = BuildConfig.ICS_FEED_URL,
    private val client: OkHttpClient = defaultIcsHttpClient(),
    private val parser: IcsCalendarParser = IcsCalendarParser(),
    private val retryBackoff: suspend (attempt: Int) -> Unit = ::defaultRetryBackoff,
) : IcsCalendarRepository {
    override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            require(feedUrl.isNotBlank()) { "ICS feed URL is not configured." }

            val request = Request.Builder()
                .url(feedUrl)
                .get()
                .build()

            fetchWithRetry(request, range)
        }

    private suspend fun fetchWithRetry(
        request: Request,
        range: DateRange,
    ): List<CalendarEvent> {
        var attempt = 1
        while (true) {
            try {
                return client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IcsHttpException(response.code)
                    }

                    val body = response.body?.string()
                        ?: throw IOException("Failed to fetch ICS feed: empty response body")
                    parser.parse(body, range)
                }
            } catch (exception: IOException) {
                if (attempt >= MAX_ATTEMPTS || !exception.isTransient()) {
                    throw exception
                }
                retryBackoff(attempt)
                attempt += 1
            }
        }
    }

    private fun IOException.isTransient(): Boolean =
        this !is IcsHttpException || code == 429 || code in 500..599

    private class IcsHttpException(
        val code: Int,
    ) : IOException("Failed to fetch ICS feed: HTTP $code")

    private companion object {
        const val MAX_ATTEMPTS = 3

        fun defaultIcsHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        suspend fun defaultRetryBackoff(attempt: Int) {
            delay(250L * (1L shl (attempt - 1)))
        }
    }
}
