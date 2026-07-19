package com.prehmus.selli.domain.model

open class CalendarAuthRequiredException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
