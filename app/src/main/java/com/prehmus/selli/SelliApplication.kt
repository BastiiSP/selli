package com.prehmus.selli

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.prehmus.selli.data.google.GoogleCalendarDataRepository
import com.prehmus.selli.data.ics.OkHttpIcsCalendarRepository
import com.prehmus.selli.data.widget.WidgetRefreshScheduler
import com.prehmus.selli.data.widget.WidgetRuntime
import com.prehmus.selli.domain.merge.DefaultCalendarMergeService
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.widget.SelliWidget

/**
 * Prozess-Einstieg: verdrahtet die Widget-Datenversorgung (Owner Codex) mit
 * der Glance-Darstellung. WorkManager startet seine Worker immer erst nach
 * [onCreate], deshalb sind die Hooks hier zuverlässig gesetzt — auch wenn nur
 * das Widget (ohne Activity) den Prozess weckt.
 */
class SelliApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        WidgetRuntime.mergeServiceFactory = { context ->
            DefaultCalendarMergeService(
                // Ohne Activity: der Widget-Worker liest nur (kein Sign-in nötig);
                // der personResolver greift ausschließlich beim Sign-in und bleibt hier ungenutzt.
                GoogleCalendarDataRepository(
                    context = context,
                    personResolver = { Person.BASTI },
                ),
                OkHttpIcsCalendarRepository(),
            )
        }
        WidgetRuntime.onSnapshotUpdated = { context -> SelliWidget().updateAll(context) }

        WidgetRefreshScheduler.ensureScheduled(this)
    }
}
