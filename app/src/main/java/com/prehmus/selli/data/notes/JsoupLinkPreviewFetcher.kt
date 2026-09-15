package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.notes.LinkPreview
import com.prehmus.selli.domain.notes.LinkPreviewFetcher
import com.prehmus.selli.domain.notes.parseLinkPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup

class JsoupLinkPreviewFetcher(
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : LinkPreviewFetcher {
    override suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                val document = Jsoup.connect(url)
                    .timeout(TIMEOUT_MILLIS.toInt())
                    .get()
                parseLinkPreview(document)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logger.error(SOURCE, error)
            null
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val SOURCE = "Link-Vorschau"
    }
}
