package com.prehmus.selli.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.LiveShareController
import com.prehmus.selli.domain.repository.LiveShareStartResult
import com.prehmus.selli.domain.repository.LocationRepository
import com.prehmus.selli.domain.repository.OwnLiveShareState
import com.prehmus.selli.ui.components.CloudIssue
import com.prehmus.selli.ui.components.toCloudIssue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Zustand der Standort-Ansicht. [isConfigured] ist false, wenn in `local.properties` keine
 * Supabase-Zugangsdaten liegen — dann zeigt der Tab einen Hinweis statt einer leeren Karte.
 */
data class LocationUiState(
    val locations: List<PersonLocation> = emptyList(),
    val isConfigured: Boolean = false,
    /** Erst nach dem ersten erfolgreichen Laden heißt „keine Positionen" wirklich keine. */
    val hasLoaded: Boolean = false,
    /** Warum die Karte gerade nicht frisch ist; die zuletzt bekannten Positionen bleiben stehen. */
    val loadIssue: CloudIssue? = null,
    /** Eigene Live-Freigabe laut lokalem Speicher; ob sie noch läuft, entscheidet die UI mit `now`. */
    val ownLiveShare: OwnLiveShareState = OwnLiveShareState(),
    /** Warum der letzte Start einer Live-Freigabe nicht geklappt hat; bis zum Wegklicken sichtbar. */
    val liveStartProblem: LiveStartProblem? = null,
)

enum class LiveStartProblem { MISSING_PRECISE_PERMISSION, NOT_CONFIGURED }

/**
 * Beobachtet die Positionen beider Personen und die eigene Live-Freigabe. Bewusst schlank: die
 * Erfassung der eigenen Position und die Live-Taktung macht der Foreground-Service, nicht dieses
 * ViewModel — es liest mit und reicht Start/Ende an den [LiveShareController] weiter.
 *
 * Der Feed endet nach Fehlern nicht, sondern versucht es selbst weiter und startet nach einer
 * Kontobestätigung neu (siehe LocationRepository.observeLocationFeed). [retry] ist nur die
 * Abkürzung für ungeduldige Daumen.
 */
class LocationViewModel(
    private val repository: LocationRepository,
    private val cloudSession: CloudSessionRepository,
    private val liveShareController: LiveShareController,
    isConfigured: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState(isConfigured = isConfigured))
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var feedJob: Job? = null

    init {
        if (isConfigured) observeFeed()
        viewModelScope.launch {
            liveShareController.state.collect { state ->
                _uiState.update { it.copy(ownLiveShare = state) }
            }
        }
    }

    fun startLiveShare(duration: LiveShareDuration) {
        val problem = when (liveShareController.start(duration)) {
            is LiveShareStartResult.Started -> null
            LiveShareStartResult.MissingPreciseLocationPermission -> LiveStartProblem.MISSING_PRECISE_PERMISSION
            LiveShareStartResult.NotConfigured -> LiveStartProblem.NOT_CONFIGURED
        }
        _uiState.update { it.copy(liveStartProblem = problem) }
    }

    fun stopLiveShare() {
        liveShareController.stop()
    }

    fun dismissLiveStartProblem() {
        _uiState.update { it.copy(liveStartProblem = null) }
    }

    fun retry() {
        if (!_uiState.value.isConfigured) return
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            cloudSession.ensureAccess()
            collectFeed()
        }
    }

    private fun observeFeed() {
        feedJob?.cancel()
        feedJob = viewModelScope.launch { collectFeed() }
    }

    private suspend fun collectFeed() {
        repository.observeLocationFeed().collect { event ->
            when (event) {
                is LocationFeedEvent.Loaded -> _uiState.update {
                    it.copy(locations = event.locations, hasLoaded = true, loadIssue = null)
                }
                is LocationFeedEvent.Failed -> _uiState.update {
                    it.copy(loadIssue = event.error.toCloudIssue())
                }
            }
        }
    }

    companion object {
        fun factory(
            repository: LocationRepository,
            cloudSession: CloudSessionRepository,
            liveShareController: LiveShareController,
            isConfigured: Boolean,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LocationViewModel(repository, cloudSession, liveShareController, isConfigured) as T
        }
    }
}
