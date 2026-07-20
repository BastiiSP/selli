package com.prehmus.selli.domain.merge

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.CalendarAuthRequiredException
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import com.prehmus.selli.domain.repository.NoOpEventCustomizationRepository
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
    private val customizationRepository: EventCustomizationRepository =
        NoOpEventCustomizationRepository,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : CalendarMergeService {

    override suspend fun mergedEvents(range: DateRange): List<CalendarEvent> = coroutineScope {
        val googleEvents = async {
            fetchEventsOrEmpty(GOOGLE_SOURCE) { googleCalendarRepository.fetchEvents(range) }
        }
        val icsEvents = async {
            fetchEventsOrEmpty(ICS_SOURCE) { icsCalendarRepository.fetchEvents(range) }
        }

        val merged = (googleEvents.await() + icsEvents.await())
            .distinctBy { event -> event.id to event.source }

        applyCustomizationsOrOriginal(merged)
            .sortedWith(
                compareByDescending<CalendarEvent> { event -> event.isAllDay }
                    .thenBy { event -> event.start },
            )
    }

    private suspend fun applyCustomizationsOrOriginal(
        events: List<CalendarEvent>,
    ): List<CalendarEvent> {
        val customizations = try {
            customizationRepository.all()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(CUSTOMIZATION_SOURCE, exception)
            emptyList()
        }

        val occurrenceCustomizations = customizations
            .mapNotNull { customization ->
                val target = customization.target as? CustomizationTarget.Occurrence
                    ?: return@mapNotNull null
                target.key to customization
            }
            .toMap()
        val seriesCustomizations = customizations.filter {
            it.target is CustomizationTarget.SeriesFrom
        }

        return events.mapNotNull { event ->
            val customization = occurrenceCustomizations[EventKey(event.source, event.id)]
                ?: seriesCustomizations.latestMatchFor(event)

            if (customization?.hidden == true) return@mapNotNull null

            val overrides = customization?.overrides ?: EventFieldOverrides()
            val resolvedCategory = overrides.category ?: deriveDefaultCategory(event)
            event.withOverrides(
                overrides = overrides,
                allowDateOverride = customization?.target is CustomizationTarget.Occurrence,
                category = resolvedCategory,
            )
        }
    }

    private fun deriveDefaultCategory(event: CalendarEvent): EventCategory = when {
        event.source == CalendarSource.WORK_ICS -> EventCategory.WORK
        event.isSharedEvent -> EventCategory.TOGETHER
        else -> EventCategory.PRIVATE
    }

    private fun List<EventCustomization>.latestMatchFor(event: CalendarEvent): EventCustomization? =
        asSequence()
            .filter { customization ->
                val target = customization.target as CustomizationTarget.SeriesFrom
                target.source == event.source &&
                    target.seriesId == event.seriesId &&
                    !event.start.isBefore(target.fromStart)
            }
            .maxByOrNull { customization ->
                (customization.target as CustomizationTarget.SeriesFrom).fromStart
            }

    private fun CalendarEvent.withOverrides(
        overrides: EventFieldOverrides,
        allowDateOverride: Boolean,
        category: EventCategory,
    ): CalendarEvent {
        val hasFieldOverride =
            overrides.title != null ||
                (allowDateOverride && overrides.date != null) ||
                (!isAllDay && (overrides.startTime != null || overrides.endTime != null)) ||
                overrides.location != null ||
                overrides.description != null
        val hasCategoryOverride = overrides.category != null
        val hasApplicableOverride = hasFieldOverride || hasCategoryOverride

        val newStart: LocalDateTime
        val newEnd: LocalDateTime
        if (!hasFieldOverride) {
            newStart = start
            newEnd = end
        } else {
            val overriddenDate = overrides.date.takeIf { allowDateOverride }
            if (isAllDay) {
                val dayShift = overriddenDate?.let { newDate ->
                    Duration.between(
                        start.toLocalDate().atStartOfDay(),
                        newDate.atStartOfDay(),
                    ).toDays()
                } ?: 0L
                newStart = start.plusDays(dayShift)
                newEnd = end.plusDays(dayShift)
            } else {
                val startDate = overriddenDate ?: start.toLocalDate()
                val endDayOffset = Duration.between(
                    start.toLocalDate().atStartOfDay(),
                    end.toLocalDate().atStartOfDay(),
                ).toDays()
                newStart = startDate.atTime(overrides.startTime ?: start.toLocalTime())
                newEnd = startDate.plusDays(endDayOffset).atTime(overrides.endTime ?: end.toLocalTime())
            }
        }

        return copy(
            title = overrides.title ?: title,
            start = newStart,
            end = newEnd,
            location = overrides.location ?: location,
            description = overrides.description ?: description,
            category = category,
            isCustomized = hasFieldOverride,
            hasAnyCustomization = hasApplicableOverride,
        )
    }

    /**
     * Liefert alle mindestens drei Stunden langen Blöcke im gemeinsamen Tagesfenster.
     * Ganztägige Events blockieren bewusst nicht, weil sie in den verbundenen Kalendern häufig
     * Marker wie Geburtstage oder Urlaub sind.
     */
    override suspend fun freeBlocks(day: LocalDate): List<FreeTimeBlock> {
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

        val free = mutableListOf<FreeTimeBlock>()
        var cursor = window.start
        for (blockedInterval in blockedIntervals) {
            if (Duration.between(cursor, blockedInterval.start) >= MINIMUM_SHARED_FREE_BLOCK) {
                free += FreeTimeBlock(cursor, blockedInterval.start)
            }
            if (blockedInterval.end > cursor) {
                cursor = blockedInterval.end
            }
        }

        if (Duration.between(cursor, window.end) >= MINIMUM_SHARED_FREE_BLOCK) {
            free += FreeTimeBlock(cursor, window.end)
        }
        return free
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
        source: String,
        fetchEvents: suspend () -> List<CalendarEvent>,
    ): List<CalendarEvent> =
        try {
            fetchEvents()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: CalendarAuthRequiredException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(source, exception)
            emptyList()
        }

    private data class TimeInterval(
        val start: LocalDateTime,
        val end: LocalDateTime,
    )

    private companion object {
        const val GOOGLE_SOURCE = "Google calendars"
        const val ICS_SOURCE = "ICS work calendar"
        const val CUSTOMIZATION_SOURCE = "Event customizations"
        val FREE_WINDOW_START: LocalTime = LocalTime.of(9, 0)
        val FREE_WINDOW_END: LocalTime = LocalTime.of(22, 0)
        val MINIMUM_SHARED_FREE_BLOCK: Duration = Duration.ofHours(3)
    }
}
