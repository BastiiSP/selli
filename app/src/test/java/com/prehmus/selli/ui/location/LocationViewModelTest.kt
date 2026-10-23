package com.prehmus.selli.ui.location

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.LocationRepository
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
        val viewModel = LocationViewModel(repository, FakeSession(), isConfigured = true)
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
        val viewModel = LocationViewModel(repository, FakeSession(), isConfigured = true)
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
        override suspend fun publishOwnLocation(location: PersonLocation): Result<Unit> = Result.success(Unit)
    }
}
