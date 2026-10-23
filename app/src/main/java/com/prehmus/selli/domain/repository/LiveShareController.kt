package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.location.LiveShare
import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.model.CloudSyncException
import kotlinx.coroutines.flow.StateFlow

data class OwnLiveShareState(
    val share: LiveShare? = null,
    /** Die gewünschte Endzeit ist für den Partner gesetzt bzw. nach Ende gelöscht. */
    val published: Boolean = false,
    val syncError: CloudSyncException? = null,
)

sealed interface LiveShareStartResult {
    data class Started(val share: LiveShare) : LiveShareStartResult
    data object MissingPreciseLocationPermission : LiveShareStartResult
    data object NotConfigured : LiveShareStartResult
}

interface LiveShareController {
    val state: StateFlow<OwnLiveShareState>
    fun start(duration: LiveShareDuration): LiveShareStartResult
    fun stop()
}
