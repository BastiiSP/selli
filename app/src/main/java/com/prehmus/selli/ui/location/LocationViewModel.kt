package com.prehmus.selli.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zustand der Standort-Ansicht. [isConfigured] ist false, wenn in `local.properties` keine
 * Supabase-Zugangsdaten liegen — dann zeigt der Tab einen Hinweis statt einer leeren Karte.
 */
data class LocationUiState(
    val locations: List<PersonLocation> = emptyList(),
    val isConfigured: Boolean = false,
)

/**
 * Beobachtet die Positionen beider Personen. Bewusst schlank: die Erfassung der eigenen
 * Position macht der Foreground-Service, nicht dieses ViewModel — es liest nur mit.
 */
class LocationViewModel(
    private val repository: LocationRepository,
    isConfigured: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState(isConfigured = isConfigured))
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        if (isConfigured) {
            viewModelScope.launch {
                // observeLocations() wirft laut Vertrag nie und liefert bei Problemen eine
                // leere Liste — deshalb hier kein zusätzliches catch.
                repository.observeLocations().collect { locations ->
                    _uiState.value = _uiState.value.copy(locations = locations)
                }
            }
        }
    }

    companion object {
        fun factory(
            repository: LocationRepository,
            isConfigured: Boolean,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LocationViewModel(repository, isConfigured) as T
        }
    }
}
