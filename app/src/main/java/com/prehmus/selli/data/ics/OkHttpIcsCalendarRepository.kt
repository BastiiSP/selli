package com.prehmus.selli.data.ics

import com.prehmus.selli.BuildConfig
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpIcsCalendarRepository(
    private val feedUrl: String = BuildConfig.ICS_FEED_URL,
    private val client: OkHttpClient = defaultIcsHttpClient(),
    private val parser: IcsCalendarParser = IcsCalendarParser(),
    private val retryBackoff: suspend (attempt: Int) -> Unit = ::defaultRetryBackoff,
    private val now: () -> Long = { System.currentTimeMillis() },
) : IcsCalendarRepository {
    override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> =
        fetchEvents(range, forceRefresh = false)

    override suspend fun fetchEvents(
        range: DateRange,
        forceRefresh: Boolean,
    ): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            require(feedUrl.isNotBlank()) { "ICS feed URL is not configured." }

            val body = cacheMutex.withLock {
                cachedFeed?.takeIf { cached ->
                    !forceRefresh && now() - cached.loadedAtMillis in 0 until CACHE_TTL_MILLIS
                }?.body ?: fetchWithRetry(
                    Request.Builder()
                        .url(feedUrl)
                        .get()
                        .build(),
                ).also { freshBody ->
                    cachedFeed = CachedFeed(freshBody, now())
                }
            }

            parser.parse(body, range)
        }

    private suspend fun fetchWithRetry(
        request: Request,
    ): String {
        var attempt = 1
        while (true) {
            try {
                return client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IcsHttpException(response.code)
                    }

                    val body = response.body?.string()
                        ?: throw IOException("Failed to fetch ICS feed: empty response body")
                    body
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

    private data class CachedFeed(
        val body: String,
        val loadedAtMillis: Long,
    )

    private val cacheMutex = Mutex()
    private var cachedFeed: CachedFeed? = null

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val CACHE_TTL_MILLIS = 15 * 60 * 1_000L

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
