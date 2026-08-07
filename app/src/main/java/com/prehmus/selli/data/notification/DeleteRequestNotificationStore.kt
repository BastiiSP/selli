package com.prehmus.selli.data.notification

import android.content.Context
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventKey

/** Merkt sich, für welche Termine schon eine Lösch-Anfrage-Benachrichtigung gezeigt wurde. */
class DeleteRequestNotificationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): Set<EventKey> =
        preferences.getStringSet(KEY_NOTIFIED, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .toSet()

    fun save(keys: Set<EventKey>) {
        preferences.edit()
            .putStringSet(KEY_NOTIFIED, keys.map(::encode).toSet())
            .apply()
    }

    private fun encode(key: EventKey): String = "${key.source.name}$SEPARATOR${key.eventId}"

    private fun decode(raw: String): EventKey? {
        val parts = raw.split(SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val source = runCatching { CalendarSource.valueOf(parts[0]) }.getOrNull() ?: return null
        return EventKey(source = source, eventId = parts[1])
    }

    private companion object {
        const val PREFS_NAME = "selli_delete_request_notifications"
        const val KEY_NOTIFIED = "notified_keys"
        const val SEPARATOR = "|"
    }
}
