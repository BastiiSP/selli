package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.EventRecurrence
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GoogleRruleFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun format(recurrence: EventRecurrence, isAllDay: Boolean): String = buildString {
        append("RRULE:FREQ=")
        append(recurrence.frequency.name)

        recurrence.until?.let { until ->
            append(";UNTIL=")
            if (isAllDay) {
                append(until.format(DateTimeFormatter.BASIC_ISO_DATE))
            } else {
                append(
                    TIMED_UNTIL_FORMATTER.format(
                        until.atTime(LocalTime.of(23, 59, 59)).atZone(zoneId).toInstant(),
                    ),
                )
            }
        }
    }

    private companion object {
        val TIMED_UNTIL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
