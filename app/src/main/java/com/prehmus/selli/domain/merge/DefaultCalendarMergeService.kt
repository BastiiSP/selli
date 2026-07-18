package com.prehmus.selli.domain.merge

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DefaultCalendarMergeService(
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val icsCalendarRepository: IcsCalendarRepository,
) : CalendarMergeService {

    override suspend fun mergedEvents(range: DateRange): List<CalendarEvent> = coroutineScope {
        val googleEvents = async {
            fetchEventsOrEmpty { googleCalendarRepository.fetchEvents(range) }
        }
        val icsEvents = async {
            fetchEventsOrEmpty { icsCalendarRepository.fetchEvents(range) }
        }

        (googleEvents.await() + icsEvents.await())
            .distinctBy { event -> event.id to event.source }
            .sortedWith(
                compareByDescending<CalendarEvent> { event -> event.isAllDay }
                    .thenBy { event -> event.start },
            )
    }

    override suspend fun isBothFree(day: LocalDate): Boolean =
        mergedEvents(DateRange(start = day, endInclusive = day))
            .none { event -> event.touches(day) }

    private fun CalendarEvent.touches(day: LocalDate): Boolean {
        val dayStart = day.atStartOfDay()
        val nextDayStart = day.plusDays(1).atStartOfDay()

        return start < nextDayStart && end > dayStart
    }

    private suspend fun fetchEventsOrEmpty(
        fetchEvents: suspend () -> List<CalendarEvent>,
    ): List<CalendarEvent> =
        try {
            fetchEvents()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            emptyList()
        }
}
