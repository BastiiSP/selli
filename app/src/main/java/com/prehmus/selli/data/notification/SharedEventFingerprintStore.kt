package com.prehmus.selli.data.notification

import android.content.Context
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.notification.SharedEventFingerprint

class SharedEventFingerprintStore(
    context: Context,
    private val codec: SharedEventFingerprintCodec = SharedEventFingerprintCodec(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): Map<EventKey, SharedEventFingerprint> {
        val values = preferences.all.mapNotNull { (key, value) ->
            (value as? String)?.let { stringValue -> key to stringValue }
        }.toMap()

        return codec.decode(values)
    }

    fun save(fingerprints: Map<EventKey, SharedEventFingerprint>) {
        preferences.edit()
            .clear()
            .apply {
                codec.encode(fingerprints).forEach { (key, value) ->
                    putString(key, value)
                }
            }
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "selli_shared_event_fingerprints"
    }
}
