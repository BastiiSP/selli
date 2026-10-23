package com.prehmus.selli.data.location

import android.content.Context
import com.prehmus.selli.domain.location.LiveShare
import com.prehmus.selli.domain.location.validAt
import java.time.Clock
import java.time.Instant

class SharedPreferencesLiveShareStorage(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : LiveShareStorage {
    private val preferences = context.applicationContext.getSharedPreferences("selli_live_share", Context.MODE_PRIVATE)

    @Synchronized
    override fun read(): LiveShare? {
        val share = runCatching {
            if (!preferences.contains("started_at") || !preferences.contains("ends_at")) return@runCatching null
            LiveShare(
                Instant.ofEpochMilli(preferences.getLong("started_at", 0L)),
                Instant.ofEpochMilli(preferences.getLong("ends_at", 0L)),
            ).validAt(clock.instant())
        }.getOrNull()
        if (share == null) write(null)
        return share
    }

    @Synchronized
    override fun write(share: LiveShare?) {
        preferences.edit().apply {
            if (share == null) {
                remove("started_at")
                remove("ends_at")
            } else {
                putLong("started_at", share.startedAt.toEpochMilli())
                putLong("ends_at", share.endsAt.toEpochMilli())
            }
        }.apply()
    }
}
