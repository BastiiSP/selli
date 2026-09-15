package com.prehmus.selli.data.widget

import android.content.Context
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.repository.ExpenseRepository
import com.prehmus.selli.domain.repository.NoteRepository

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

    @Volatile
    var expenseRepositoryFactory: (() -> ExpenseRepository)? = null

    @Volatile
    var noteRepositoryFactory: (() -> NoteRepository)? = null
}
