package com.prehmus.selli.ui.location

import com.prehmus.selli.domain.location.LiveShare
import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.LiveShareController
import com.prehmus.selli.domain.repository.LiveShareStartResult
import com.prehmus.selli.domain.repository.LocationRepository
import com.prehmus.selli.domain.repository.OwnLiveShareState
import com.prehmus.selli.ui.calendar.MainDispatcherRule
import com.prehmus.selli.ui.components.CloudIssue
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `failed feed followed by loaded positions clears issue`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeLocations()
        val viewModel = LocationViewModel(repository, FakeSession(), FakeLiveShareController(), isConfigured = true)
        runCurrent()

        repository.events.emit(LocationFeedEvent.Failed(CloudSyncException.Offline()))
        runCurrent()
        assertFalse(viewModel.uiState.value.hasLoaded)
        assertTrue(viewModel.uiState.value.locations.isEmpty())
        assertEquals(CloudIssue.OFFLINE, viewModel.uiState.value.loadIssue)

        val locations = listOf(location())
        repository.events.emit(LocationFeedEvent.Loaded(locations))
        runCurrent()
        assertEquals(locations, viewModel.uiState.value.locations)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertNull(viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `failed feed preserves last loaded positions`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeLocations()
        val viewModel = LocationViewModel(repository, FakeSession(), FakeLiveShareController(), isConfigured = true)
        runCurrent()

        val locations = listOf(location())
        repository.events.emit(LocationFeedEvent.Loaded(locations))
        runCurrent()
        repository.events.emit(LocationFeedEvent.Failed(CloudSyncException.Offline()))
        runCurrent()

        assertEquals(locations, viewModel.uiState.value.locations)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertEquals(CloudIssue.OFFLINE, viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `controller state reaches ui state including later publication error`() = runTest(mainDispatcherRule.dispatcher) {
        val share = LiveShare(Instant.EPOCH, Instant.EPOCH.plusSeconds(900))
        val controller = FakeLiveShareController(OwnLiveShareState(share = share))
        val viewModel = LocationViewModel(FakeLocations(), FakeSession(), controller, isConfigured = true)
        runCurrent()
        assertEquals(share, viewModel.uiState.value.ownLiveShare.share)
        assertFalse(viewModel.uiState.value.ownLiveShare.published)

        controller.mutableState.value = OwnLiveShareState(
            share = share,
            published = true,
            syncError = CloudSyncException.Offline(),
        )
        runCurrent()
        assertEquals(share, viewModel.uiState.value.ownLiveShare.share)
        assertTrue(viewModel.uiState.value.ownLiveShare.published)
        assertTrue(viewModel.uiState.value.ownLiveShare.syncError is CloudSyncException.Offline)
    }

    @Test
    fun `successful start passes duration and clears previous problem`() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakeLiveShareController()
        val viewModel = LocationViewModel(FakeLocations(), FakeSession(), controller, isConfigured = true)
        controller.startResult = LiveShareStartResult.NotConfigured
        viewModel.startLiveShare(LiveShareDuration.MINUTES_15)
        assertEquals(LiveStartProblem.NOT_CONFIGURED, viewModel.uiState.value.liveStartProblem)

        controller.startResult = LiveShareStartResult.Started(
            LiveShare(Instant.EPOCH, Instant.EPOCH.plusSeconds(3_600)),
        )
        viewModel.startLiveShare(LiveShareDuration.HOUR_1)
        assertNull(viewModel.uiState.value.liveStartProblem)
        assertEquals(listOf(LiveShareDuration.MINUTES_15, LiveShareDuration.HOUR_1), controller.startedDurations)
    }

    @Test
    fun `missing precise permission reports problem and dismiss clears it`() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakeLiveShareController()
        controller.startResult = LiveShareStartResult.MissingPreciseLocationPermission
        val viewModel = LocationViewModel(FakeLocations(), FakeSession(), controller, isConfigured = true)

        viewModel.startLiveShare(LiveShareDuration.HOURS_8)
        assertEquals(LiveStartProblem.MISSING_PRECISE_PERMISSION, viewModel.uiState.value.liveStartProblem)
        assertEquals(listOf(LiveShareDuration.HOURS_8), controller.startedDurations)
        viewModel.dismissLiveStartProblem()
        assertNull(viewModel.uiState.value.liveStartProblem)
    }

    @Test
    fun `not configured start preserves loaded positions`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeLocations()
        val controller = FakeLiveShareController()
        controller.startResult = LiveShareStartResult.NotConfigured
        val viewModel = LocationViewModel(repository, FakeSession(), controller, isConfigured = true)
        runCurrent()
        val locations = listOf(location())
        repository.events.emit(LocationFeedEvent.Loaded(locations))
        runCurrent()

        viewModel.startLiveShare(LiveShareDuration.MINUTES_15)
        assertEquals(LiveStartProblem.NOT_CONFIGURED, viewModel.uiState.value.liveStartProblem)
        assertEquals(locations, viewModel.uiState.value.locations)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertNull(viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `stop forwards to controller`() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakeLiveShareController()
        val viewModel = LocationViewModel(FakeLocations(), FakeSession(), controller, isConfigured = true)

        viewModel.stopLiveShare()
        assertEquals(1, controller.stopCalls)
    }

    private fun location() = PersonLocation(
        person = Person.BASTI,
        latitude = 52.0,
        longitude = 13.0,
        accuracyMeters = 12.0,
        speedMetersPerSecond = null,
        isMoving = false,
        updatedAt = Instant.EPOCH,
    )

    private class FakeSession : CloudSessionRepository {
        override val accessState: StateFlow<CloudAccessState> = MutableStateFlow(CloudAccessState.CONNECTED)
        override suspend fun ensureAccess(): Result<Unit> = Result.success(Unit)
        override suspend fun reconfirmInteractively(): CloudReconfirmResult = CloudReconfirmResult.Success
    }

    private class FakeLocations : LocationRepository {
        val events = MutableSharedFlow<LocationFeedEvent>()
        override fun observeLocationFeed(): Flow<LocationFeedEvent> = events
        override suspend fun publishLiveUntil(liveUntil: Instant?): Result<Boolean> = Result.success(true)
        override suspend fun publishOwnLocation(location: PersonLocation): Result<Unit> = Result.success(Unit)
    }

    private class FakeLiveShareController(initialState: OwnLiveShareState = OwnLiveShareState()) : LiveShareController {
        val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<OwnLiveShareState> = mutableState
        var startResult: LiveShareStartResult = LiveShareStartResult.NotConfigured
        val startedDurations = mutableListOf<LiveShareDuration>()
        var stopCalls = 0

        override fun start(duration: LiveShareDuration): LiveShareStartResult {
            startedDurations += duration
            return startResult
        }

        override fun stop() {
            stopCalls++
        }
    }
}
