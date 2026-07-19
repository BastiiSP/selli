package com.prehmus.selli.data.widget

import android.content.Context
import com.prehmus.selli.domain.model.WidgetSnapshot

class WidgetSnapshotStore(
    context: Context,
    private val codec: WidgetSnapshotCodec = WidgetSnapshotCodec(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): WidgetSnapshot? {
        val values = preferences.all.mapNotNull { (key, value) ->
            (value as? String)?.let { stringValue -> key to stringValue }
        }.toMap()

        return codec.decode(values)
    }

    fun save(snapshot: WidgetSnapshot) {
        preferences.edit()
            .clear()
            .apply {
                codec.encode(snapshot).forEach { (key, value) ->
                    putString(key, value)
                }
            }
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "selli_widget"
    }
}
