package com.prehmus.selli.data.google

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.model.CalendarEvent
import kotlinx.coroutines.CancellationException

internal class IndependentGoogleCalendarEventFetcher(
    private val logger: CalendarLogger,
) {
    suspend fun fetch(
        fetchOwn: suspend () -> List<CalendarEvent>,
        fetchPartner: suspend () -> List<CalendarEvent>,
    ): List<CalendarEvent> {
        val ownEvents = fetchOwn()
        val partnerEvents = try {
            fetchPartner()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(PARTNER_SOURCE, exception)
            emptyList()
        }

        return ownEvents + partnerEvents
    }

    private companion object {
        const val PARTNER_SOURCE = "Google partner calendar"
    }
}
