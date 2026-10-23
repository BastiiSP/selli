package com.prehmus.selli.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.ui.components.CloudReconfirmUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Gemeinsamer Bestätigungs-Weg für Ideen, Kosten und Standort. Liegt einmal im Gerüst statt
 * pro Tab, damit ein Tippen auf „Konto bestätigen" in einem Bereich alle drei wieder
 * freischaltet und nicht zwei Kontodialoge gleichzeitig aufgehen.
 *
 * Bestätigt wird ausschließlich das bereits verknüpfte eigene Google-Konto; die
 * Partnerverknüpfung bleibt unberührt (siehe CloudSessionRepository.reconfirmInteractively).
 */
class CloudAccessViewModel(
    private val repository: CloudSessionRepository,
) : ViewModel() {
    private val _reconfirm = MutableStateFlow(CloudReconfirmUi())
    val reconfirm: StateFlow<CloudReconfirmUi> = _reconfirm.asStateFlow()

    fun reconfirm() {
        if (_reconfirm.value.isRunning) return
        _reconfirm.value = CloudReconfirmUi(isRunning = true)
        viewModelScope.launch {
            val hint = when (val result = repository.reconfirmInteractively()) {
                CloudReconfirmResult.Success -> null
                CloudReconfirmResult.Cancelled -> "Bestätigung abgebrochen."
                is CloudReconfirmResult.WrongAccount ->
                    "Bitte wähle das verknüpfte Konto ${result.expectedEmail}."
                is CloudReconfirmResult.Failed ->
                    "Das hat nicht geklappt. Prüfe deine Verbindung und versuch es erneut."
            }
            _reconfirm.update { CloudReconfirmUi(isRunning = false, hint = hint) }
        }
    }

    companion object {
        fun factory(repository: CloudSessionRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CloudAccessViewModel(repository) as T
            }
    }
}
