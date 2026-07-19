package com.prehmus.selli.data.widget

import android.content.Context
import com.prehmus.selli.domain.CalendarMergeService

/**
 * Wird von der App-Verdrahtung beim Prozessstart gesetzt.
 *
 * WorkManager startet Worker erst nach `Application.onCreate`, sodass die Factory und der
 * optionale Widget-Callback hier fuer Hintergrundlaeufe bereitstehen.
 */
object WidgetRuntime {
    @Volatile
    var mergeServiceFactory: ((Context) -> CalendarMergeService)? = null

    @Volatile
    var onSnapshotUpdated: (suspend (Context) -> Unit)? = null
}
