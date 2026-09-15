package com.prehmus.selli.data.notification

import android.content.Context
import android.content.SharedPreferences

/** Merkt sich, welche Ideen-Punkt-IDs schon einmal gesehen/gemeldet wurden. */
class NoteItemSeenStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()

    fun save(ids: Set<String>) {
        preferences.edit().putStringSet(KEY_SEEN_IDS, ids).apply()
    }

    private companion object {
        const val PREFS_NAME = "selli_note_notifications"
        const val KEY_SEEN_IDS = "seen_note_item_ids"
    }
}
