package com.prehmus.selli.data.customization

import com.google.gson.Gson
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EventCustomizationCodec(
    private val gson: Gson = Gson(),
) {
    fun encode(customizations: List<EventCustomization>): String =
        gson.toJson(
            Document(
                version = FORMAT_VERSION,
                customizations = customizations.map { customization ->
                    customization.toStoredCustomization()
                },
            ),
        )

    fun decode(json: String): List<EventCustomization> {
        val document = requireNotNull(gson.fromJson(json, Document::class.java)) {
            "Customization document must not be null."
        }
        require(document.version == FORMAT_VERSION) {
            "Unsupported customization document version '${document.version}'."
        }
        return document.customizations.map { customization -> customization.toDomain() }
    }

    private fun EventCustomization.toStoredCustomization(): StoredCustomization {
        val occurrence = target as? CustomizationTarget.Occurrence
        val seriesFrom = target as? CustomizationTarget.SeriesFrom
        return StoredCustomization(
            targetType = if (occurrence != null) TARGET_OCCURRENCE else TARGET_SERIES_FROM,
            source = occurrence?.key?.source?.name ?: requireNotNull(seriesFrom).source.name,
            eventId = occurrence?.key?.eventId,
            seriesId = seriesFrom?.seriesId,
            fromStart = seriesFrom?.fromStart?.toString(),
            hidden = hidden,
            title = overrides.title,
            date = overrides.date?.toString(),
            startTime = overrides.startTime?.toString(),
            endTime = overrides.endTime?.toString(),
            location = overrides.location,
            description = overrides.description,
            category = overrides.category?.name,
            label = label,
        )
    }

    private fun StoredCustomization.toDomain(): EventCustomization {
        val calendarSource = CalendarSource.valueOf(source)
        val target = when (targetType) {
            TARGET_OCCURRENCE -> CustomizationTarget.Occurrence(
                EventKey(
                    source = calendarSource,
                    eventId = requireNotNull(eventId) { "Occurrence target is missing eventId." },
                ),
            )
            TARGET_SERIES_FROM -> CustomizationTarget.SeriesFrom(
                source = calendarSource,
                seriesId = requireNotNull(seriesId) { "SeriesFrom target is missing seriesId." },
                fromStart = LocalDateTime.parse(
                    requireNotNull(fromStart) { "SeriesFrom target is missing fromStart." },
                ),
            )
            else -> error("Unknown customization target type '$targetType'.")
        }
        return EventCustomization(
            target = target,
            hidden = hidden,
            overrides = EventFieldOverrides(
                title = title,
                date = date?.let { value -> LocalDate.parse(value) },
                startTime = startTime?.let { value -> LocalTime.parse(value) },
                endTime = endTime?.let { value -> LocalTime.parse(value) },
                location = location,
                description = description,
                category = category?.let { value -> EventCategory.valueOf(value) },
            ),
            label = label,
        )
    }

    private data class Document(
        val version: Int,
        val customizations: List<StoredCustomization>,
    )

    private data class StoredCustomization(
        val targetType: String,
        val source: String,
        val eventId: String?,
        val seriesId: String?,
        val fromStart: String?,
        val hidden: Boolean,
        val title: String?,
        val date: String?,
        val startTime: String?,
        val endTime: String?,
        val location: String?,
        val description: String?,
        val category: String?,
        val label: String,
    )

    private companion object {
        const val FORMAT_VERSION = 1
        const val TARGET_OCCURRENCE = "occurrence"
        const val TARGET_SERIES_FROM = "series_from"
    }
}
