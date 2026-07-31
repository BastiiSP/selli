package com.prehmus.selli.domain

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.MergedCalendar
import java.time.LocalDate

interface CalendarMergeService {
    suspend fun mergedEvents(range: DateRange): List<CalendarEvent>
    suspend fun mergedEventsWithStatus(range: DateRange): MergedCalendar =
        MergedCalendar(mergedEvents(range), emptyList())
    suspend fun mergedEventsWithStatus(range: DateRange, forceRefresh: Boolean): MergedCalendar =
        mergedEventsWithStatus(range)
    suspend fun freeBlocks(day: LocalDate): List<FreeTimeBlock>
    suspend fun freeBlocksInRange(range: DateRange): Map<LocalDate, List<FreeTimeBlock>>
    suspend fun isBothFree(day: LocalDate): Boolean = freeBlocks(day).isNotEmpty()
}
