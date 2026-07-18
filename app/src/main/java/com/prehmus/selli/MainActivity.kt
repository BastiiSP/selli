package com.prehmus.selli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.prehmus.selli.ui.SelliApp
import com.prehmus.selli.ui.preview.PreviewDependencies
import com.prehmus.selli.ui.theme.SelliTheme

/**
 * Einstiegspunkt: baut die Abhängigkeiten und übergibt sie an [SelliApp].
 * Aktuell laufen hier noch die Preview-Fakes — die Verdrahtung auf die echten
 * Codex-Implementierungen (Google Calendar, ICS, Merge) folgt, sobald deren
 * Konstruktor-Signaturen final sind (Phase 3 des MVP-Handovers).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dependencies = PreviewDependencies()
        setContent {
            SelliTheme {
                SelliApp(dependencies = dependencies)
            }
        }
    }
}
