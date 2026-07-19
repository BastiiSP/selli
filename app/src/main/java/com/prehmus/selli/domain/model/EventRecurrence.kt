package com.prehmus.selli.domain.model

import java.time.LocalDate

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

data class EventRecurrence(
    val frequency: RecurrenceFrequency,
    /** Letzter Tag (inklusive), an dem ein Vorkommen noch stattfinden darf; null = endlos. */
    val until: LocalDate? = null,
)
