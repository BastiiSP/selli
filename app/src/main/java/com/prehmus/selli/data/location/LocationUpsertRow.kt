package com.prehmus.selli.data.location

import com.prehmus.selli.domain.model.PersonLocation
import java.time.format.DateTimeFormatter
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class LocationUpsertRow(
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
) {
    companion object
}

internal fun LocationUpsertRow.Companion.from(location: PersonLocation, userId: String): LocationUpsertRow = LocationUpsertRow(
    userId = userId,
    person = location.person.name,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracyMeters = location.accuracyMeters,
    speedMps = location.speedMetersPerSecond,
    isMoving = location.isMoving,
    updatedAt = DateTimeFormatter.ISO_INSTANT.format(location.updatedAt),
)
