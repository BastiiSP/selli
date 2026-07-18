package com.prehmus.selli.data.ics

import com.prehmus.selli.BuildConfig
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpIcsCalendarRepository(
    private val feedUrl: String = BuildConfig.ICS_FEED_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val parser: IcsCalendarParser = IcsCalendarParser(),
) : IcsCalendarRepository {
    override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            require(feedUrl.isNotBlank()) { "ICS feed URL is not configured." }

            val request = Request.Builder()
                .url(feedUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to fetch ICS feed: HTTP ${response.code}")
                }

                val body = response.body?.string()
                    ?: throw IOException("Failed to fetch ICS feed: empty response body")
                parser.parse(body, range)
            }
        }
}
