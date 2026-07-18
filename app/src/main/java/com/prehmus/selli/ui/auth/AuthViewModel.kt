package com.prehmus.selli.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.AuthResult
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Anmelde-Zustand: Google-Sign-in, danach einmalige Partner-Verknüpfung
 * (automatischer ACL-Insert für gegenseitige Termin-Einsicht — kein manuelles
 * Teilen in den Google-Einstellungen nötig).
 */
sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    data object SigningIn : AuthUiState

    /** Angemeldet — jetzt noch die Kalender gegenseitig freigeben. */
    data class ConnectPartner(
        val account: Account,
        val isConnecting: Boolean = false,
        val errorMessage: String? = null,
    ) : AuthUiState

    /** Beide Kalender verknüpft — die App kann losbelegen. */
    data class Ready(val account: Account) : AuthUiState

    data class SignInError(val message: String) : AuthUiState
}

class AuthViewModel(
    private val googleCalendarRepository: GoogleCalendarRepository,
    /**
     * Meldet der Verdrahtung (MainActivity), als wer sich die Person angemeldet hat —
     * der personResolver der Google-Anbindung liest diesen Wert. Die UI fragt das
     * vor dem Sign-in ab ("Wer bist du?"), weil signIn() laut Vertrag parameterlos ist.
     */
    private val rememberOwnPerson: (Person) -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.SignedOut)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(ownPerson: Person) {
        if (_uiState.value is AuthUiState.SigningIn) return
        rememberOwnPerson(ownPerson)
        _uiState.value = AuthUiState.SigningIn
        viewModelScope.launch {
            when (val result = googleCalendarRepository.signIn()) {
                is AuthResult.Success -> _uiState.value = AuthUiState.ConnectPartner(result.account)
                is AuthResult.Error -> _uiState.value = AuthUiState.SignInError(result.message)
                AuthResult.Cancelled -> _uiState.value = AuthUiState.SignedOut
            }
        }
    }

    fun connectPartner(partnerEmail: String) {
        val current = _uiState.value as? AuthUiState.ConnectPartner ?: return
        if (current.isConnecting) return
        val trimmed = partnerEmail.trim()
        if (trimmed.isBlank() || "@" !in trimmed) {
            _uiState.value = current.copy(errorMessage = "Bitte eine gültige E-Mail-Adresse angeben.")
            return
        }
        _uiState.value = current.copy(isConnecting = true, errorMessage = null)
        viewModelScope.launch {
            val partnerPerson = if (current.account.person == Person.BASTI) Person.MELLI else Person.BASTI
            val partner = Account(
                id = trimmed,
                email = trimmed,
                displayName = if (partnerPerson == Person.MELLI) "Melli" else "Basti",
                person = partnerPerson,
            )
            googleCalendarRepository.grantMutualAccess(current.account, partner)
                .onSuccess { _uiState.value = AuthUiState.Ready(current.account) }
                .onFailure { error ->
                    _uiState.value = current.copy(
                        isConnecting = false,
                        errorMessage = error.message ?: "Freigabe fehlgeschlagen — bitte nochmal versuchen.",
                    )
                }
        }
    }

    fun dismissError() {
        if (_uiState.value is AuthUiState.SignInError) {
            _uiState.value = AuthUiState.SignedOut
        }
    }

    companion object {
        fun factory(
            googleCalendarRepository: GoogleCalendarRepository,
            rememberOwnPerson: (Person) -> Unit = {},
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AuthViewModel(googleCalendarRepository, rememberOwnPerson) as T
        }
    }
}
