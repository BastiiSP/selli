package com.prehmus.selli.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.prehmus.selli.data.widget.WidgetRefreshScheduler

/**
 * Broadcast-Receiver des Homescreen-Widgets. Sobald das erste Widget auf dem
 * Homescreen landet, sorgt er dafür, dass die periodische Aktualisierung
 * (WorkManager, Owner Codex) läuft und einmal sofort frische Daten geholt werden.
 */
class SelliWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SelliWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.ensureScheduled(context)
        WidgetRefreshScheduler.refreshNow(context)
    }
}
