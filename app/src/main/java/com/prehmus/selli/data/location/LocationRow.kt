package com.prehmus.selli.data.location

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LocationRow(
    @SerialName("user_id") val userId: String,
    @SerialName("person") val person: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @EncodeDefault
    @SerialName("accuracy_meters")
    val accuracyMeters: Double? = null,
    @EncodeDefault
    @SerialName("speed_mps")
    val speedMps: Double? = null,
    @EncodeDefault
    @SerialName("is_moving")
    val isMoving: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("live_until") val liveUntil: String? = null,
) {
    companion object
}

fun LocationRow.toDomain(): PersonLocation? {
    val mappedPerson = runCatching { Person.valueOf(person) }.getOrNull() ?: return null
    val mappedUpdatedAt = runCatching { OffsetDateTime.parse(updatedAt).toInstant() }.getOrNull()
        ?: return null

    return PersonLocation(
        person = mappedPerson,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMps,
        isMoving = isMoving,
        updatedAt = mappedUpdatedAt,
        liveUntil = liveUntil?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
    )
}

fun LocationRow.Companion.from(location: PersonLocation, userId: String): LocationRow = LocationRow(
    userId = userId,
    person = location.person.name,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracyMeters = location.accuracyMeters,
    speedMps = location.speedMetersPerSecond,
    isMoving = location.isMoving,
    updatedAt = DateTimeFormatter.ISO_INSTANT.format(location.updatedAt),
    liveUntil = location.liveUntil?.let(DateTimeFormatter.ISO_INSTANT::format),
)
