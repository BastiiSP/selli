package com.prehmus.selli.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.SessionState
import com.prehmus.selli.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Was der Einstellungsbereich über die Verknüpfung weiß — mehr braucht er bewusst nicht. */
data class SettingsUiState(
    val ownAccount: Account? = null,
    val partnerAccount: Account? = null,
)

/**
 * Liest den gespeicherten Verknüpfungsstatus für die Profilanzeige. Bewusst schreibfrei:
 * „Konto wechseln" läuft weiterhin über den [com.prehmus.selli.ui.auth.AuthViewModel],
 * damit es genau eine Stelle gibt, die den Anmeldezustand verändert.
 */
class SettingsViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = runCatching { sessionRepository.sessionState() }
                .getOrDefault(SessionState.SignedOut)
            _uiState.value = when (session) {
                is SessionState.Linked ->
                    SettingsUiState(session.ownAccount, session.partnerAccount)
                is SessionState.NeedsPartner -> SettingsUiState(session.ownAccount, null)
                SessionState.SignedOut -> SettingsUiState()
            }
        }
    }

    companion object {
        fun factory(sessionRepository: SessionRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(sessionRepository) as T
        }
    }
}
