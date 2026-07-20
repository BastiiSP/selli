package com.prehmus.selli.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class Person { BASTI, MELLI }

enum class CalendarSource { GOOGLE_OWN, GOOGLE_PARTNER, WORK_ICS }

data class Account(
    val id: String,
    val email: String,
    val displayName: String,
    val person: Person,
)

sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult
    data object Cancelled : AuthResult
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean,
    val source: CalendarSource,
    val owner: Person,
    val isSharedEvent: Boolean,
    val location: String? = null,
    val description: String? = null,
    val seriesId: String? = null,
    val isCustomized: Boolean = false,
    val category: EventCategory = EventCategory.PRIVATE,
)

data class NewCalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val invitePartner: Boolean = false,
    val recurrence: EventRecurrence? = null,
)

enum class EventCategory { WORK, PRIVATE, TOGETHER }
