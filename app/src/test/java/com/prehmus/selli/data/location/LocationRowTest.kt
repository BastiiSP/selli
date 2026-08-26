package com.prehmus.selli.data.location

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRowTest {
    private val json = Json

    @Test
    fun `from and toDomain preserve every field`() {
        val location = PersonLocation(
            person = Person.MELLI,
            latitude = 52.520008,
            longitude = 13.404954,
            accuracyMeters = 7.5,
            speedMetersPerSecond = 3.25,
            isMoving = true,
            updatedAt = Instant.parse("2026-08-26T14:03:11.482Z"),
        )

        val mapped = LocationRow.from(location, userId = "user-123").toDomain()

        assertEquals(location, mapped)
    }

    @Test
    fun `serialization uses exact database column names`() {
        val row = LocationRow(
            userId = "user-123",
            person = "BASTI",
            latitude = 52.52,
            longitude = 13.405,
            accuracyMeters = 8.0,
            speedMps = null,
            isMoving = false,
            updatedAt = "2026-08-26T14:03:11.482Z",
        )

        val keys = json.parseToJsonElement(json.encodeToString(row)).jsonObject.keys

        assertEquals(
            setOf(
                "user_id",
                "person",
                "latitude",
                "longitude",
                "accuracy_meters",
                "speed_mps",
                "is_moving",
                "updated_at",
            ),
            keys,
        )
    }

    @Test
    fun `postgres json with offset and microseconds maps to instant`() {
        val payload = """
            {
              "user_id": "user-123",
              "person": "MELLI",
              "latitude": 52.520008,
              "longitude": 13.404954,
              "accuracy_meters": 6.75,
              "speed_mps": 1.5,
              "is_moving": true,
              "updated_at": "2026-08-26T14:03:11.482123+00:00"
            }
        """.trimIndent()

        val location = json.decodeFromString<LocationRow>(payload).toDomain()

        assertEquals(Person.MELLI, location?.person)
        assertEquals(Instant.parse("2026-08-26T14:03:11.482123Z"), location?.updatedAt)
        assertEquals(52.520008, location?.latitude ?: 0.0, 0.0)
        assertEquals(13.404954, location?.longitude ?: 0.0, 0.0)
        assertEquals(6.75, location?.accuracyMeters ?: 0.0, 0.0)
        assertEquals(1.5, location?.speedMetersPerSecond ?: 0.0, 0.0)
        assertTrue(location?.isMoving == true)
    }

    @Test
    fun `unknown person maps to null`() {
        assertNull(validRow(person = "UNKNOWN").toDomain())
    }

    @Test
    fun `unparseable updated at maps to null`() {
        assertNull(validRow(updatedAt = "not-a-timestamp").toDomain())
    }

    private fun validRow(
        person: String = "BASTI",
        updatedAt: String = "2026-08-26T14:03:11.482123+00:00",
    ) = LocationRow(
        userId = "user-123",
        person = person,
        latitude = 52.52,
        longitude = 13.405,
        accuracyMeters = null,
        speedMps = null,
        isMoving = false,
        updatedAt = updatedAt,
    )
}
