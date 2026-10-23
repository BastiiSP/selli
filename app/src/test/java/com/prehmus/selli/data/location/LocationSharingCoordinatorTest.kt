package com.prehmus.selli.data.location

import com.prehmus.selli.domain.location.LiveShare
import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.location.SharingMode
import com.prehmus.selli.domain.location.cadence
import com.prehmus.selli.domain.location.startLiveShare
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.LocationRepository
import com.prehmus.selli.domain.repository.OwnLiveShareState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSharingCoordinatorTest {
    private val clock = MutableClock()
    private val storage = MemoryStorage()
    private val repository = FakeRepository()
    private val state = MutableStateFlow(OwnLiveShareState())
    private var elapsed = 0L

    @Test
    fun `start and process restart preserve every duration`() = runTest {
        for (duration in LiveShareDuration.entries) {
            val share = startLiveShare(clock.instant(), duration)
            storage.write(share)
            val first = coordinator()
            assertEquals(SharingMode.LIVE, first.mode)
            assertEquals(share, state.value.share)
            assertEquals(clock.instant().plus(duration.duration), state.value.share?.endsAt)
            first.reconcile()
            assertTrue(state.value.published)
            val restarted = coordinator()
            assertEquals(SharingMode.LIVE, restarted.mode)
            assertEquals(share, state.value.share)
            assertFalse(state.value.published)
            restarted.reconcile()
            assertEquals(share.endsAt, repository.endTimes.last())
            assertTrue(state.value.published)
        }
    }

    @Test
    fun `stop clears storage and publishes null then every normal fix uploads`() = runTest {
        start()
        val sharing = coordinator()
        sharing.reconcile()
        sharing.offer(fix(speed = 3f))
        sharing.uploadLatest()
        assertTrue(repository.positions.last().isMoving)
        sharing.stop()
        assertNull(storage.read())
        assertEquals(SharingMode.NORMAL, sharing.mode)
        assertEquals(900_000L, sharing.mode.cadence().intervalMillis)
        sharing.reconcile()
        assertNull(repository.endTimes.last())
        assertTrue(state.value.published)
        repeat(2) {
            sharing.offer(fix(speed = 3f))
            sharing.uploadLatest()
        }
        assertEquals(3, repository.positions.size)
        assertFalse(repository.positions.last().isMoving)
    }

    @Test
    fun `expiry clears persisted share and late fix uses normal upload rules`() = runTest {
        val share = start()
        val sharing = coordinator()
        sharing.reconcile()
        sharing.offer(fix())
        sharing.uploadLatest()
        clock.now = share.endsAt
        assertEquals(SharingMode.NORMAL, sharing.mode)
        assertNull(storage.read())
        sharing.reconcile()
        assertNull(repository.endTimes.last())
        sharing.offer(fix(speed = 10f))
        sharing.uploadLatest()
        assertEquals(2, repository.positions.size)
        assertEquals(clock.instant(), repository.positions.last().updatedAt)
        assertFalse(repository.positions.last().isMoving)
        assertNull(state.value.share)
        clock.now = share.startedAt
        assertEquals(SharingMode.NORMAL, sharing.mode)
    }

    @Test
    fun `expired restart and excessive clock rollback never republish live`() = runTest {
        val share = start()
        clock.now = share.endsAt.plusSeconds(1)
        val expired = coordinator()
        assertEquals(SharingMode.NORMAL, expired.mode)
        expired.reconcile()
        assertNull(storage.read())
        assertEquals(listOf<Instant?>(null), repository.endTimes)
        storage.write(share)
        clock.now = share.endsAt.minus(Duration.ofHours(8)).minusSeconds(61)
        val invalid = coordinator()
        assertEquals(SharingMode.NORMAL, invalid.mode)
        invalid.reconcile()
        assertNull(storage.read())
        assertTrue(repository.endTimes.all { it == null })
    }

    @Test
    fun `live fixes respect spacing haversine movement accuracy and heartbeat`() = runTest {
        start()
        val sharing = coordinator()
        sharing.offer(fix(speed = 2.5f))
        sharing.uploadLatest()
        assertFalse(repository.positions.single().isMoving)
        elapsed = 9_999L
        sharing.offer(fix(latitude = 50.001))
        sharing.uploadLatest()
        assertEquals(1, repository.positions.size)
        elapsed = 10_000L
        sharing.offer(fix(latitude = 50.0001)) // Etwa 11 m, unter der Bewegungsschwelle.
        sharing.uploadLatest()
        assertEquals(1, repository.positions.size)
        sharing.offer(fix(latitude = 50.0003, accuracy = 50f)) // Etwa 33 m, unter der Genauigkeit.
        sharing.uploadLatest()
        assertEquals(1, repository.positions.size)
        sharing.offer(fix(latitude = 50.001, speed = 3f))
        sharing.uploadLatest()
        assertEquals(2, repository.positions.size)
        assertTrue(repository.positions.last().isMoving)
        elapsed = 69_999L
        sharing.offer(fix(latitude = 50.01, accuracy = 300f))
        sharing.uploadLatest()
        assertEquals(2, repository.positions.size)
        elapsed = 70_000L
        sharing.offer(fix(latitude = 50.01, accuracy = null))
        sharing.uploadLatest()
        assertEquals(3, repository.positions.size)
    }

    @Test
    fun `live publication error is visible and next reconciliation recovers`() = runTest {
        val share = start()
        repository.liveResult = Result.failure(CloudSyncException.Offline())
        val sharing = coordinator()
        sharing.reconcile()
        assertFalse(state.value.published)
        assertTrue(state.value.syncError is CloudSyncException.Offline)
        assertEquals(CloudSyncException.Offline().message, state.value.syncError?.message)
        assertEquals(share, state.value.share)
        repository.liveResult = Result.success(true)
        sharing.reconcile()
        assertEquals(listOf(share.endsAt, share.endsAt), repository.endTimes)
        assertTrue(state.value.published)
        assertNull(state.value.syncError)
    }

    @Test
    fun `missing row retries live publication after first successful upsert`() = runTest {
        val share = start()
        repository.hasRow = false
        val sharing = coordinator()
        sharing.reconcile()
        assertFalse(state.value.published)
        assertNull(state.value.syncError)
        sharing.offer(fix())
        sharing.uploadLatest()
        assertEquals(listOf(share.endsAt, share.endsAt), repository.endTimes)
        assertTrue(state.value.published)
    }

    @Test
    fun `stop during position upload prevents waiting reconciliation from setting live`() = runTest {
        start()
        val sharing = coordinator()
        val gate = CompletableDeferred<Unit>()
        repository.positionGate = gate
        sharing.offer(fix())
        val uploading = launch { sharing.uploadLatest() }
        runCurrent()
        val waiting = launch { sharing.reconcile() }
        runCurrent()
        sharing.stop()
        assertEquals(SharingMode.NORMAL, sharing.mode)
        gate.complete(Unit)
        uploading.join()
        waiting.join()
        assertEquals(listOf<Instant?>(null), repository.endTimes)
        assertTrue(state.value.published)
        assertNull(state.value.share)
    }

    @Test
    fun `stop during live request corrects both delayed success and delayed failure`() = runTest {
        for (fails in listOf(false, true)) {
            start()
            val sharing = coordinator()
            val gate = CompletableDeferred<Unit>()
            repository.liveGate = gate
            repository.failGatedLive = fails
            val requestStart = repository.endTimes.size
            val sending = launch { sharing.reconcile() }
            runCurrent()
            sharing.stop()
            val late = launch { sharing.reconcile() }
            runCurrent()
            gate.complete(Unit)
            sending.join()
            late.join()
            assertEquals(2, repository.endTimes.size - requestStart)
            assertNull(repository.endTimes.last())
            assertNull(state.value.share)
            assertTrue(state.value.published)
            assertNull(state.value.syncError)
        }
    }

    @Test
    fun `network failure retains only newest measurement and reconnect uploads it`() = runTest {
        start()
        val sharing = coordinator()
        val gate = CompletableDeferred<Unit>()
        repository.positionGate = gate
        repository.positionResult = Result.failure(CloudSyncException.Offline())
        sharing.offer(fix())
        val upload = launch { sharing.uploadLatest() }
        runCurrent()
        clock.now = clock.now.plusSeconds(10)
        sharing.offer(fix(latitude = 51.0))
        clock.now = clock.now.plusSeconds(10)
        val latest = fix(latitude = 52.0)
        sharing.offer(latest)
        sharing.offer(latest.copy(latitude = 49.0, measuredAt = latest.measuredAt.minusSeconds(1)))
        gate.complete(Unit)
        upload.join()
        repository.positionResult = Result.success(Unit)
        sharing.uploadLatest()
        assertEquals(listOf(50.0, 52.0), repository.positions.map { it.latitude })
        assertEquals(latest.measuredAt, repository.positions.last().updatedAt)
        sharing.uploadLatest()
        assertEquals(2, repository.positions.size)
    }

    @Test
    fun `failed position upload does not prevent stop publication`() = runTest {
        start()
        val sharing = coordinator()
        sharing.reconcile()
        val gate = CompletableDeferred<Unit>()
        repository.positionGate = gate
        repository.positionResult = Result.failure(CloudSyncException.Offline())
        sharing.offer(fix())
        val upload = launch { sharing.uploadLatest() }
        runCurrent()
        sharing.stop()
        gate.complete(Unit)
        upload.join()
        assertNull(repository.endTimes.last())
        assertTrue(state.value.published)
        assertNull(state.value.share)
    }

    @Test
    fun `thrown errors remain visible without terminating measurements`() = runTest {
        start()
        val sharing = coordinator()
        repository.liveException = IllegalStateException("test failure")
        sharing.reconcile()
        assertTrue(state.value.syncError is CloudSyncException.Failed)
        assertEquals(CloudSyncException.Failed().message, state.value.syncError?.message)
        assertFalse(sharing.isSyncing)
        repository.liveException = null
        sharing.offer(fix())
        sharing.uploadLatest()
        assertEquals(1, repository.positions.size)
        assertTrue(state.value.published)
        assertNull(state.value.syncError)
    }

    @Test
    fun `cancellation releases serialization without becoming a cloud failure`() = runTest {
        start()
        val sharing = coordinator()
        repository.liveException = CancellationException("cancelled")
        val job = launch { sharing.reconcile() }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(sharing.isSyncing)
        assertNull(state.value.syncError)
        repository.liveException = null
        sharing.reconcile()
        assertTrue(state.value.published)
    }

    private fun start(): LiveShare = startLiveShare(clock.instant(), LiveShareDuration.MINUTES_15).also(storage::write)

    private fun coordinator() = LocationSharingCoordinator(
        repository = repository,
        person = Person.BASTI,
        storage = storage,
        state = state,
        clock = clock,
        elapsedMillis = { elapsed },
    )

    private fun fix(latitude: Double = 50.0, accuracy: Float? = 5f, speed: Float? = null) = LocationFix(
        latitude = latitude,
        longitude = 10.0,
        accuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        measuredAt = clock.instant(),
    )

    private class MemoryStorage : LiveShareStorage {
        private var share: LiveShare? = null
        override fun read(): LiveShare? = share
        override fun write(share: LiveShare?) {
            this.share = share
        }
    }

    private class MutableClock : Clock() {
        var now: Instant = Instant.parse("2026-10-23T12:00:00Z")
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }

    private class FakeRepository : LocationRepository {
        val endTimes = mutableListOf<Instant?>()
        val positions = mutableListOf<PersonLocation>()
        var hasRow = true
        var liveResult = Result.success(true)
        var positionResult = Result.success(Unit)
        var positionGate: CompletableDeferred<Unit>? = null
        var liveGate: CompletableDeferred<Unit>? = null
        var failGatedLive = false
        var liveException: Exception? = null

        override fun observeLocationFeed(): Flow<LocationFeedEvent> = emptyFlow()

        override suspend fun publishLiveUntil(liveUntil: Instant?): Result<Boolean> {
            endTimes += liveUntil
            liveException?.let { throw it }
            val gate = liveGate
            liveGate = null
            if (gate != null) {
                gate.await()
                if (failGatedLive) return Result.failure(CloudSyncException.Offline())
            }
            return if (hasRow) liveResult else Result.success(false)
        }

        override suspend fun publishOwnLocation(location: PersonLocation): Result<Unit> {
            positions += location
            val gate = positionGate
            positionGate = null
            gate?.await()
            if (positionResult.isSuccess) hasRow = true
            return positionResult
        }
    }
}
