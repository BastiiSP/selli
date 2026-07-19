package com.prehmus.selli.domain.merge

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarAuthRequiredException
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

    /**
     * Prüft, ob im gemeinsamen Tagesfenster noch mindestens ein zusammenhängender freier Block
     * übrig ist. Ganztägige Events blockieren bewusst nicht, weil sie in den verbundenen Kalendern
     * häufig Marker wie Geburtstage oder Urlaub sind.
     */
    override suspend fun isBothFree(day: LocalDate): Boolean {
        val window = TimeInterval(
            start = day.atTime(FREE_WINDOW_START),
            end = day.atTime(FREE_WINDOW_END),
        )
        val blockedIntervals = mergedEvents(DateRange(start = day, endInclusive = day))
            .asSequence()
            .filterNot { event -> event.isAllDay }
            .mapNotNull { event -> event.blockedIntervalIn(window) }
            .sortedBy { interval -> interval.start }
            .toList()
            .mergeTouching()

        var cursor = window.start
        for (blockedInterval in blockedIntervals) {
            if (Duration.between(cursor, blockedInterval.start) >= MINIMUM_SHARED_FREE_BLOCK) {
                return true
            }
            if (blockedInterval.end > cursor) {
                cursor = blockedInterval.end
            }
        }

        return Duration.between(cursor, window.end) >= MINIMUM_SHARED_FREE_BLOCK
    }

    private fun CalendarEvent.blockedIntervalIn(window: TimeInterval): TimeInterval? {
        val clippedStart = maxOf(start, window.start)
        val clippedEnd = minOf(end, window.end)

        return if (clippedStart < clippedEnd) {
            TimeInterval(start = clippedStart, end = clippedEnd)
        } else {
            null
        }
    }

    private fun List<TimeInterval>.mergeTouching(): List<TimeInterval> {
        if (isEmpty()) return emptyList()

        val merged = mutableListOf<TimeInterval>()
        var current = first()

        for (interval in drop(1)) {
            current = if (interval.start <= current.end) {
                current.copy(end = maxOf(current.end, interval.end))
            } else {
                merged += current
                interval
            }
        }

        merged += current
        return merged
    }

    private suspend fun fetchEventsOrEmpty(
        fetchEvents: suspend () -> List<CalendarEvent>,
    ): List<CalendarEvent> =
        try {
            fetchEvents()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: CalendarAuthRequiredException) {
            throw exception
        } catch (exception: Exception) {
            emptyList()
        }

    private data class TimeInterval(
        val start: LocalDateTime,
        val end: LocalDateTime,
    )

    private companion object {
        val FREE_WINDOW_START: LocalTime = LocalTime.of(9, 0)
        val FREE_WINDOW_END: LocalTime = LocalTime.of(22, 0)
        val MINIMUM_SHARED_FREE_BLOCK: Duration = Duration.ofHours(3)
    }
}
