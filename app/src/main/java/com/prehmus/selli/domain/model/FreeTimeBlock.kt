package com.prehmus.selli.domain.model

import java.time.Duration
import java.time.LocalDateTime

data class FreeTimeBlock(
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    val duration: Duration
        get() = Duration.between(start, end)
}
