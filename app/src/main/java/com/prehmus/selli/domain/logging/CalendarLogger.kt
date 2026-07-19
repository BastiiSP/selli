package com.prehmus.selli.domain.logging

fun interface CalendarLogger {
    fun error(source: String, cause: Throwable)
}

object NoOpCalendarLogger : CalendarLogger {
    override fun error(source: String, cause: Throwable) = Unit
}
