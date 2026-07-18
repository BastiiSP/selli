package com.prehmus.selli.data.ics

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.Person
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Small RFC-5545 parser for the published MS365 work-calendar feed.
 *
 * Recurrence expansion is deliberately best-effort: DAILY and WEEKLY rules support INTERVAL, COUNT,
 * UNTIL, BYDAY, and EXDATE inside the requested range. More complex frequencies such as MONTHLY or
 * YEARLY are returned only as their DTSTART occurrence.
 */
class IcsCalendarParser(
    private val systemZone: ZoneId = ZoneId.systemDefault(),
) {
    fun parse(ics: String, range: DateRange): List<CalendarEvent> {
        return parseVEvents(ics)
            .filterNot { it.property("STATUS")?.value.equals("CANCELLED", ignoreCase = true) }
            .flatMap { it.toCalendarEvents(range) }
            .filter { it.intersects(range) }
            .sortedWith(compareBy<CalendarEvent> { it.start }.thenBy { it.id })
    }

    private fun parseVEvents(ics: String): List<IcsEvent> {
        val events = mutableListOf<IcsEvent>()
        var currentProperties: MutableList<IcsProperty>? = null

        for (line in unfoldLines(ics)) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> currentProperties = mutableListOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    currentProperties?.let { events += IcsEvent(it) }
                    currentProperties = null
                }
                currentProperties != null -> parseProperty(line)?.let { currentProperties += it }
            }
        }

        return events
    }

    private fun unfoldLines(ics: String): List<String> {
        val normalized = ics.replace("\r\n", "\n").replace('\r', '\n')
        val result = mutableListOf<String>()
        for (line in normalized.split('\n')) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.drop(1)
            } else {
                result += line
            }
        }
        return result
    }

    private fun parseProperty(line: String): IcsProperty? {
        val colonIndex = line.indexOf(':')
        if (colonIndex < 0) return null

        val left = line.substring(0, colonIndex)
        val value = line.substring(colonIndex + 1)
        val parts = left.split(';')
        val name = parts.first().uppercase(Locale.ROOT)
        val parameters = parts.drop(1)
            .mapNotNull { parameter ->
                val equalsIndex = parameter.indexOf('=')
                if (equalsIndex < 0) {
                    null
                } else {
                    val key = parameter.substring(0, equalsIndex).uppercase(Locale.ROOT)
                    val parameterValue = parameter.substring(equalsIndex + 1).trim('"')
                    key to parameterValue
                }
            }
            .toMap()

        return IcsProperty(name = name, parameters = parameters, value = value)
    }

    private fun IcsEvent.toCalendarEvents(range: DateRange): List<CalendarEvent> {
        val startProperty = property("DTSTART") ?: return emptyList()
        val start = parseDateTime(startProperty)
        val endProperty = property("DTEND")
        val parsedEnd = endProperty?.let(::parseDateTime)
        val end = when {
            parsedEnd != null -> parsedEnd.dateTime
            start.isAllDay -> start.dateTime.plusDays(1)
            else -> start.dateTime
        }
        val durationSeconds = Duration.between(start.dateTime, end).seconds
        val uid = property("UID")?.value?.ifBlank { null } ?: start.dateTime.toString()
        val title = property("SUMMARY")?.value?.unescapeText().orEmpty()
        val location = property("LOCATION")?.value?.unescapeText()?.ifBlank { null }
        val description = property("DESCRIPTION")?.value?.unescapeText()?.ifBlank { null }
        val base = ParsedEvent(
            uid = uid,
            title = title,
            start = start.dateTime,
            durationSeconds = durationSeconds,
            isAllDay = start.isAllDay,
            location = location,
            description = description,
        )
        val rrule = property("RRULE")?.value?.let(::parseRRule)
        val exdates = properties("EXDATE").flatMap { parseExDates(it) }.toSet()
        val occurrenceStarts = expandOccurrences(base.start, rrule, exdates, range)

        return occurrenceStarts.map { occurrenceStart ->
            val occurrenceEnd = occurrenceStart.plusSeconds(durationSeconds)
            base.toCalendarEvent(
                id = if (rrule == null) uid else "$uid-${occurrenceStart.format(occurrenceIdFormatter)}",
                start = occurrenceStart,
                end = occurrenceEnd,
            )
        }
    }

    private fun expandOccurrences(
        start: LocalDateTime,
        rrule: RRule?,
        exdates: Set<LocalDateTime>,
        range: DateRange,
    ): List<LocalDateTime> {
        if (rrule == null || rrule.frequency !in setOf("DAILY", "WEEKLY")) {
            return listOf(start).filterNot { it in exdates }
        }

        val rangeEndExclusive = range.endInclusive.plusDays(1).atStartOfDay()
        val maxUntil = rrule.until ?: rangeEndExclusive
        val occurrences = mutableListOf<LocalDateTime>()
        var producedByRule = 0
        if (rrule.count == 0) return occurrences

        if (rrule.frequency == "DAILY") {
            val byDays = rrule.byDays
            var current = start
            while (!current.isAfter(maxUntil) && current.isBefore(rangeEndExclusive)) {
                if (byDays.isEmpty() || current.dayOfWeek in byDays) {
                    producedByRule++
                    if (current !in exdates) {
                        occurrences += current
                    }
                    if (rrule.count != null && producedByRule >= rrule.count) break
                }
                current = current.plusDays(rrule.interval.toLong())
            }
            return occurrences
        }

        val byDays = rrule.byDays.ifEmpty { setOf(start.dayOfWeek) }
        val weekStart = start.toLocalDate()
        var weekOffset = 0L
        while (true) {
            val weekBase = weekStart.plusWeeks(weekOffset)
            val weekOccurrences = byDays
                .map { day -> weekBase.plusDays((day.value - start.dayOfWeek.value).toLong()) }
                .map { date -> LocalDateTime.of(date, start.toLocalTime()) }
                .filterNot { it.isBefore(start) }
                .sorted()

            for (candidate in weekOccurrences) {
                if (candidate.isAfter(maxUntil) || !candidate.isBefore(rangeEndExclusive)) {
                    return occurrences
                }
                producedByRule++
                if (candidate !in exdates) {
                    occurrences += candidate
                }
                if (rrule.count != null && producedByRule >= rrule.count) {
                    return occurrences
                }
            }

            weekOffset += rrule.interval.toLong()
        }
    }

    private fun parseRRule(value: String): RRule {
        val values = value.split(';')
            .mapNotNull { part ->
                val equalsIndex = part.indexOf('=')
                if (equalsIndex < 0) {
                    null
                } else {
                    part.substring(0, equalsIndex).uppercase(Locale.ROOT) to part.substring(equalsIndex + 1)
                }
            }
            .toMap()

        return RRule(
            frequency = values["FREQ"]?.uppercase(Locale.ROOT).orEmpty(),
            interval = values["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            until = values["UNTIL"]?.let { parseDateTimeValue(it, emptyMap()).dateTime },
            count = values["COUNT"]?.toIntOrNull()?.coerceAtLeast(0),
            byDays = values["BYDAY"]
                ?.split(',')
                ?.mapNotNull { day -> dayOfWeekByCode[day.takeLast(2).uppercase(Locale.ROOT)] }
                ?.toSet()
                .orEmpty(),
        )
    }

    private fun parseExDates(property: IcsProperty): List<LocalDateTime> {
        return property.value
            .split(',')
            .filter { it.isNotBlank() }
            .map { parseDateTimeValue(it, property.parameters).dateTime }
    }

    private fun parseDateTime(property: IcsProperty): ParsedDateTime {
        return parseDateTimeValue(property.value, property.parameters)
    }

    private fun parseDateTimeValue(value: String, parameters: Map<String, String>): ParsedDateTime {
        val isAllDay = parameters["VALUE"].equals("DATE", ignoreCase = true) || dateRegex.matches(value)
        if (isAllDay) {
            return ParsedDateTime(
                dateTime = LocalDate.parse(value, dateFormatter).atStartOfDay(),
                isAllDay = true,
            )
        }

        val localValue = if (value.endsWith("Z", ignoreCase = true)) value.dropLast(1) else value
        val localDateTime = LocalDateTime.parse(localValue, dateTimeFormatter)
        val dateTime = when {
            value.endsWith("Z", ignoreCase = true) -> localDateTime
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(systemZone)
                .toLocalDateTime()
            parameters["TZID"] != null -> localDateTime
                .atZone(resolveZoneId(parameters.getValue("TZID")))
                .withZoneSameInstant(systemZone)
                .toLocalDateTime()
            else -> localDateTime
        }
        return ParsedDateTime(dateTime = dateTime, isAllDay = false)
    }

    private fun resolveZoneId(tzid: String): ZoneId {
        return runCatching { ZoneId.of(tzid) }
            .getOrElse { windowsZoneIds[tzid] ?: systemZone }
    }

    private fun String.unescapeText(): String {
        val result = StringBuilder(length)
        var escaped = false
        for (char in this) {
            if (escaped) {
                result.append(
                    when (char) {
                        'n', 'N' -> '\n'
                        '\\' -> '\\'
                        ';' -> ';'
                        ',' -> ','
                        else -> char
                    },
                )
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                result.append(char)
            }
        }
        if (escaped) result.append('\\')
        return result.toString()
    }

    private fun CalendarEvent.intersects(range: DateRange): Boolean {
        val rangeStart = range.start.atStartOfDay()
        val rangeEndExclusive = range.endInclusive.plusDays(1).atStartOfDay()
        return if (end.isAfter(start)) {
            start.isBefore(rangeEndExclusive) && end.isAfter(rangeStart)
        } else {
            !start.isBefore(rangeStart) && start.isBefore(rangeEndExclusive)
        }
    }

    private data class IcsProperty(
        val name: String,
        val parameters: Map<String, String>,
        val value: String,
    )

    private data class IcsEvent(
        val properties: List<IcsProperty>,
    ) {
        fun property(name: String): IcsProperty? = properties(name).firstOrNull()

        fun properties(name: String): List<IcsProperty> {
            return properties.filter { it.name.equals(name, ignoreCase = true) }
        }
    }

    private data class ParsedDateTime(
        val dateTime: LocalDateTime,
        val isAllDay: Boolean,
    )

    private data class ParsedEvent(
        val uid: String,
        val title: String,
        val start: LocalDateTime,
        val durationSeconds: Long,
        val isAllDay: Boolean,
        val location: String?,
        val description: String?,
    ) {
        fun toCalendarEvent(id: String, start: LocalDateTime, end: LocalDateTime): CalendarEvent {
            return CalendarEvent(
                id = id,
                title = title,
                start = start,
                end = end,
                isAllDay = isAllDay,
                source = CalendarSource.WORK_ICS,
                owner = Person.BASTI,
                isSharedEvent = false,
                location = location,
                description = description,
            )
        }
    }

    private data class RRule(
        val frequency: String,
        val interval: Int,
        val until: LocalDateTime?,
        val count: Int?,
        val byDays: Set<DayOfWeek>,
    )

    private companion object {
        val dateRegex = Regex("""\d{8}""")
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        val occurrenceIdFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        val windowsZoneIds = mapOf(
            "Central European Standard Time" to ZoneId.of("Europe/Berlin"),
            "Romance Standard Time" to ZoneId.of("Europe/Berlin"),
            "W. Europe Standard Time" to ZoneId.of("Europe/Berlin"),
        )
        val dayOfWeekByCode = mapOf(
            "MO" to DayOfWeek.MONDAY,
            "TU" to DayOfWeek.TUESDAY,
            "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY,
            "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY,
        )
    }
}
