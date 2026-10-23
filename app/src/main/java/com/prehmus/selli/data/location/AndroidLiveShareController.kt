package com.prehmus.selli.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.prehmus.selli.data.supabase.SupabaseRuntime
import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.location.startLiveShare
import com.prehmus.selli.domain.location.validAt
import com.prehmus.selli.domain.repository.LiveShareController
import com.prehmus.selli.domain.repository.LiveShareStartResult
import com.prehmus.selli.domain.repository.OwnLiveShareState
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidLiveShareController(
    private val context: Context,
    private val storage: LiveShareStorage,
    private val mutableState: MutableStateFlow<OwnLiveShareState>,
    private val clock: Clock = Clock.systemUTC(),
) : LiveShareController {
    override val state = mutableState.asStateFlow()

    init {
        synchronized(storage) {
            val stored = storage.read()
            val share = stored.validAt(clock.instant())
            if (stored != null && share == null) storage.write(null)
            if (mutableState.value.share != share) mutableState.value = OwnLiveShareState(share = share)
        }
    }

    override fun start(duration: LiveShareDuration): LiveShareStartResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return LiveShareStartResult.MissingPreciseLocationPermission
        }
        if (SupabaseRuntime.client?.isConfigured != true) return LiveShareStartResult.NotConfigured
        val share = synchronized(storage) {
            startLiveShare(clock.instant(), duration).also {
                storage.write(it)
                mutableState.value = OwnLiveShareState(share = it)
            }
        }
        SelliLocationService.sync(context)
        return LiveShareStartResult.Started(share)
    }

    override fun stop() {
        synchronized(storage) {
            storage.write(null)
            mutableState.value = OwnLiveShareState()
        }
        SelliLocationService.sync(context)
    }
}
