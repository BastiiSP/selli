package com.prehmus.selli

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository

/**
 * Verdrahtungspunkt zwischen UI (Claude) und Logik (Codex): MainActivity baut
 * hieraus die ViewModels. Die konkreten Implementierungen liefern die
 * Codex-Module unter data/ und domain/.
 */
interface AppDependencies {
    val googleCalendarRepository: GoogleCalendarRepository
    val icsCalendarRepository: IcsCalendarRepository
    val calendarMergeService: CalendarMergeService
    val calendarRepository: CalendarRepository
}
