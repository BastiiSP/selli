package com.prehmus.selli.data.location

import android.content.Context
import com.prehmus.selli.domain.location.validAt
import com.prehmus.selli.domain.repository.LiveShareController
import com.prehmus.selli.domain.repository.OwnLiveShareState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/** Gemeinsamer Zustand für Dienst und UI, auch wenn der Dienst zuerst gestartet wird. */
object LiveShareRuntime {
    internal val mutableState = MutableStateFlow(OwnLiveShareState())
    private var liveShareStorage: LiveShareStorage? = null
    private var liveShareController: LiveShareController? = null

    @Synchronized
    internal fun storage(context: Context): LiveShareStorage = liveShareStorage ?: run {
        SharedPreferencesLiveShareStorage(context.applicationContext).also {
            mutableState.value = OwnLiveShareState(share = it.read().validAt(Instant.now()))
            liveShareStorage = it
        }
    }

    @Synchronized
    fun controller(context: Context): LiveShareController = liveShareController ?: run {
        AndroidLiveShareController(context.applicationContext, storage(context), mutableState).also {
            liveShareController = it
        }
    }
}
