package com.prehmus.selli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.prehmus.selli.data.widget.WidgetRefreshScheduler
import com.prehmus.selli.ui.SelliApp
import com.prehmus.selli.ui.theme.SelliTheme

/**
 * Einstiegspunkt: baut die produktiven Abhängigkeiten (Google Calendar, ICS,
 * Merge — Implementierungen Owner Codex) und übergibt sie an [SelliApp].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dependencies = DefaultAppDependencies(this)
        // App-Öffnen ist ein guter Moment für frische Widget-Daten (MVP-Sync-Modell).
        WidgetRefreshScheduler.refreshNow(this)
        setContent {
            SelliTheme {
                SelliApp(
                    dependencies = dependencies,
                    rememberOwnPerson = dependencies::rememberOwnPerson,
                )
            }
        }
    }
}
