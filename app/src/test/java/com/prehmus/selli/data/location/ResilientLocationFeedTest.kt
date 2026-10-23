package com.prehmus.selli.data.location

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResilientLocationFeedTest {
    @Test
    fun `failed initial load resumes immediately on connected and remains open`() = runTest {
        val source = FakeSource()
        val events = mutableListOf<LocationFeedEvent>()
        val job = backgroundScope.launch { ResilientLocationFeed(source).observe().toList(events) }
        runCurrent()
        assertTrue(events.single() is LocationFeedEvent.Failed)
        source.result = Result.success(listOf(POINT))
        source.accessState.value = CloudAccessState.CONNECTED
        runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(LocationFeedEvent.Loaded(listOf(POINT)), events.last())
        assertTrue(job.isActive)
        job.cancel()
        runCurrent()
        assertTrue(source.realtimeClosed)
    }

    @Test
    fun `backoff is five fifteen thirty then sixty seconds`() = runTest {
        val source = FakeSource()
        backgroundScope.launch { ResilientLocationFeed(source).observe().collect {} }
        runCurrent()
        assertEquals(1, source.loads)
        for ((index, wait) in listOf(5_000L, 15_000L, 30_000L, 60_000L, 60_000L).withIndex()) {
            advanceTimeBy(wait - 1)
            runCurrent()
            assertEquals(index + 1, source.loads)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(index + 2, source.loads)
        }
    }

    @Test
    fun `polling failure emits error and recovers without ending feed`() = runTest {
        val source = FakeSource().apply { result = Result.success(listOf(POINT)) }
        val events = mutableListOf<LocationFeedEvent>()
        val job = backgroundScope.launch { ResilientLocationFeed(source).observe().toList(events) }
        runCurrent()
        source.result = Result.failure(CloudSyncException.Offline())
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(events.last() is LocationFeedEvent.Failed)
        source.result = Result.success(listOf(POINT))
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(events.last() is LocationFeedEvent.Loaded)
        assertTrue(job.isActive)
    }

    @Test
    fun `not configured emits one error and completes`() = runTest {
        val source = FakeSource(configured = false)
        val events = ResilientLocationFeed(source).observe().toList()
        assertTrue((events.single() as LocationFeedEvent.Failed).error is CloudSyncException.NotConfigured)
        assertEquals(0, source.loads)
        assertFalse(source.realtimeClosed)
    }

    @Test
    fun `live polling switches from fifteen to sixty seconds after expiry`() = runTest {
        val start = POINT.updatedAt
        val source = FakeSource().apply {
            result = Result.success(listOf(POINT.copy(liveUntil = start.plusSeconds(30))))
        }
        backgroundScope.launch {
            ResilientLocationFeed(source, clock = { start.plusMillis(testScheduler.currentTime) }).observe().collect {}
        }
        runCurrent()
        assertEquals(1, source.loads)
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(1, source.loads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, source.loads)
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(3, source.loads)
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(3, source.loads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(4, source.loads)
    }

    @Test
    fun `normal polling switches to live after a successful load`() = runTest {
        val source = FakeSource().apply { result = Result.success(listOf(POINT)) }
        backgroundScope.launch {
            ResilientLocationFeed(source, clock = { POINT.updatedAt }).observe().collect {}
        }
        runCurrent()
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(1, source.loads)
        source.result = Result.success(listOf(POINT.copy(liveUntil = POINT.updatedAt.plusSeconds(900))))
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, source.loads)
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(2, source.loads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, source.loads)
    }

    private class FakeSource(configured: Boolean = true) : LocationFeedSource {
        override val isConfigured = configured
        override val accessState = MutableStateFlow(CloudAccessState.RECONFIRMATION_REQUIRED)
        var result: Result<List<PersonLocation>> = Result.failure(CloudSyncException.ReconfirmationRequired())
        var loads = 0
        var realtimeClosed = false
        override suspend fun load(): Result<List<PersonLocation>> {
            loads++
            return result
        }
        override fun changes(): Flow<Result<Unit>> = flow {
            try {
                awaitCancellation()
            } finally {
                realtimeClosed = true
            }
        }
    }

    private companion object {
        val POINT = PersonLocation(
            person = Person.BASTI,
            latitude = 50.0,
            longitude = 10.0,
            accuracyMeters = 10.0,
            speedMetersPerSecond = null,
            isMoving = false,
            updatedAt = Instant.parse("2026-10-23T12:00:00Z"),
        )
    }
}
