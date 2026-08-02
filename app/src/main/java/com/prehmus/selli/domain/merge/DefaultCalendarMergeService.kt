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
import com.prehmus.selli.domain.model.MergedCalendar
import com.prehmus.selli.domain.model.SourceLoadError
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import com.prehmus.selli.domain.repository.NoOpEventCustomizationRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultCalendarMergeService(
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val icsCalendarRepository: IcsCalendarRepository,
    private val customizationRepository: EventCustomizationRepository =
        NoOpEventCustomizationRepository,
    private val logger: CalendarLogger = NoOpCalendarLogger,
    private val melliIcsCalendarRepository: IcsCalendarRepository? = null,
) : CalendarMergeService {
    private val lastSuccessfulEventsMutex = Mutex()
    private val lastSuccessfulEvents = mutableMapOf<SourceRangeKey, List<CalendarEvent>>()

    override suspend fun mergedEvents(range: DateRange): List<CalendarEvent> =
        mergedEventsWithStatus(range).events

    override suspend fun mergedEventsWithStatus(range: DateRange): MergedCalendar =
        mergedEventsWithStatus(range, forceRefresh = false)

    override suspend fun mergedEventsWithStatus(
        range: DateRange,
        forceRefresh: Boolean,
    ): MergedCalendar = coroutineScope {
        val googleEvents = async {
            fetchSource(GOOGLE_SOURCE, GOOGLE_DISPLAY_NAME, range) {
                googleCalendarRepository.fetchEvents(range, forceRefresh)
            }
        }
        val icsEvents = async {
            fetchSource(BASTI_ICS_SOURCE, BASTI_ICS_DISPLAY_NAME, range) {
                icsCalendarRepository.fetchEvents(range, forceRefresh)
            }
        }
        val melliIcsEvents = melliIcsCalendarRepository?.let { repository ->
            async {
                fetchSource(MELLI_ICS_SOURCE, MELLI_ICS_DISPLAY_NAME, range) {
                    repository.fetchEvents(range, forceRefresh)
                }
            }
        }

        val sourceResults = listOfNotNull(
            googleEvents.await(),
            icsEvents.await(),
            melliIcsEvents?.await(),
        )
        val merged = sourceResults.flatMap { result -> result.events }
            .distinctBy { event -> Triple(event.id, event.source, event.owner) }

        val events = applyCustomizationsOrOriginal(merged)
            .sortedWith(
                compareByDescending<CalendarEvent> { event -> event.isAllDay }
                    .thenBy { event -> event.start },
            )
        MergedCalendar(
            events = events,
            errors = sourceResults.mapNotNull { result -> result.error },
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
                (!isAllDay &&
                    (overrides.endDate != null ||
                        overrides.startTime != null ||
                        overrides.endTime != null)) ||
                overrides.location != null ||
                overrides.description != null
        val hasCategoryOverride = overrides.category != null
        val hasFreeTimeOverride = overrides.blocksSharedFreeTime != null
        val hasApplicableOverride =
            hasFieldOverride || hasCategoryOverride || hasFreeTimeOverride

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
                val endDate = overrides.endDate ?: startDate.plusDays(endDayOffset)
                newEnd = endDate.atTime(overrides.endTime ?: end.toLocalTime())
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
            blocksSharedFreeTime = overrides.blocksSharedFreeTime ?: blocksSharedFreeTime,
        )
    }

    /**
     * Die Rechnung selbst liegt in [SharedFreeTimeCalculator] — hier wird nur noch geladen.
     * So kann der Widget-Job dieselbe Logik auf seine ohnehin schon geholten 30 Tage anwenden,
     * ohne pro Tag erneut zu laden.
     */
    override suspend fun freeBlocks(day: LocalDate): List<FreeTimeBlock> =
        SharedFreeTimeCalculator.freeBlocksOn(
            day = day,
            events = mergedEvents(DateRange(start = day, endInclusive = day)),
        )

    override suspend fun freeBlocksInRange(
        range: DateRange,
    ): Map<LocalDate, List<FreeTimeBlock>> =
        SharedFreeTimeCalculator.freeBlocksInRange(range, mergedEvents(range))

    private suspend fun fetchSource(
        source: String,
        displayName: String,
        range: DateRange,
        fetchEvents: suspend () -> List<CalendarEvent>,
    ): SourceResult =
        try {
            val events = fetchEvents()
            lastSuccessfulEventsMutex.withLock {
                lastSuccessfulEvents[SourceRangeKey(source, range)] = events.toList()
            }
            SourceResult(events = events)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: CalendarAuthRequiredException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(source, exception)
            SourceResult(
                events = lastSuccessfulEventsMutex.withLock {
                    lastSuccessfulEvents[SourceRangeKey(source, range)].orEmpty()
                },
                error = SourceLoadError(
                    displayName = displayName,
                    message = exception.message.orEmpty(),
                ),
            )
        }

    private data class SourceResult(
        val events: List<CalendarEvent>,
        val error: SourceLoadError? = null,
    )

    private data class SourceRangeKey(
        val source: String,
        val range: DateRange,
    )

    private companion object {
        const val GOOGLE_SOURCE = "Google calendars"
        const val BASTI_ICS_SOURCE = "Basti ICS work calendar"
        const val MELLI_ICS_SOURCE = "Melli ICS work calendar"
        const val GOOGLE_DISPLAY_NAME = "Google-Kalender"
        const val BASTI_ICS_DISPLAY_NAME = "Bastis Arbeitskalender"
        const val MELLI_ICS_DISPLAY_NAME = "Mellis Arbeitskalender"
        const val CUSTOMIZATION_SOURCE = "Event customizations"
    }
}
