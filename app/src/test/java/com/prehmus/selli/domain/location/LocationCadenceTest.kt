package com.prehmus.selli.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationCadenceTest {
    @Test
    fun `stationary without movement stays stationary`() {
        assertEquals(
            TrackingMode.STATIONARY,
            nextTrackingMode(
                current = TrackingMode.STATIONARY,
                speedMetersPerSecond = null,
                metersSinceLastUpload = 0f,
                millisSinceLastMovement = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `speed above threshold starts moving`() {
        assertEquals(
            TrackingMode.MOVING,
            nextTrackingMode(
                current = TrackingMode.STATIONARY,
                speedMetersPerSecond = MOVING_SPEED_THRESHOLD_MPS + 0.1f,
                metersSinceLastUpload = 0f,
                millisSinceLastMovement = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `distance above threshold starts moving without speed`() {
        assertEquals(
            TrackingMode.MOVING,
            nextTrackingMode(
                current = TrackingMode.STATIONARY,
                speedMetersPerSecond = null,
                metersSinceLastUpload = MOVING_DISTANCE_THRESHOLD_METERS + 1f,
                millisSinceLastMovement = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `speed exactly at threshold does not start moving`() {
        assertEquals(
            TrackingMode.STATIONARY,
            nextTrackingMode(
                current = TrackingMode.STATIONARY,
                speedMetersPerSecond = MOVING_SPEED_THRESHOLD_MPS,
                metersSinceLastUpload = 0f,
                millisSinceLastMovement = 0L,
            ),
        )
    }

    @Test
    fun `moving stays moving while movement continues`() {
        assertEquals(
            TrackingMode.MOVING,
            nextTrackingMode(
                current = TrackingMode.MOVING,
                speedMetersPerSecond = MOVING_SPEED_THRESHOLD_MPS + 0.1f,
                metersSinceLastUpload = 0f,
                millisSinceLastMovement = STATIONARY_GRACE_MILLIS,
            ),
        )
    }

    @Test
    fun `moving stays moving during stationary grace period`() {
        assertEquals(
            TrackingMode.MOVING,
            nextTrackingMode(
                current = TrackingMode.MOVING,
                speedMetersPerSecond = 0f,
                metersSinceLastUpload = 0f,
                millisSinceLastMovement = STATIONARY_GRACE_MILLIS - 1,
            ),
        )
    }

    @Test
    fun `moving becomes stationary exactly at grace period`() {
        assertEquals(
            TrackingMode.STATIONARY,
            nextTrackingMode(
                current = TrackingMode.MOVING,
                speedMetersPerSecond = 0f,
                metersSinceLastUpload = 0f,
                millisSinceLastMovement = STATIONARY_GRACE_MILLIS,
            ),
        )
    }

    @Test
    fun `settings match stationary cadence`() {
        assertEquals(
            CadenceSettings(
                intervalMillis = 300_000,
                minUpdateDistanceMeters = 150f,
                maxUpdateDelayMillis = 900_000,
                highAccuracy = false,
            ),
            TrackingMode.STATIONARY.settings(),
        )
    }

    @Test
    fun `settings match moving cadence`() {
        assertEquals(
            CadenceSettings(
                intervalMillis = 30_000,
                minUpdateDistanceMeters = 50f,
                maxUpdateDelayMillis = 60_000,
                highAccuracy = true,
            ),
            TrackingMode.MOVING.settings(),
        )
    }
}
