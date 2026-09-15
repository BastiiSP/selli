package com.prehmus.selli

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.prehmus.selli.data.notification.PartnerActivityNotifier
import com.prehmus.selli.data.notification.SharedEventNotifier
import com.prehmus.selli.data.widget.WidgetRefreshScheduler
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.ui.SelliApp
import com.prehmus.selli.ui.SelliDeepLink
import com.prehmus.selli.ui.theme.SelliTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Einstiegspunkt: baut die produktiven Abhängigkeiten (Google Calendar, ICS,
 * Merge — Implementierungen Owner Codex) und übergibt sie an [SelliApp].
 */
class MainActivity : ComponentActivity() {

    // Antippen einer Wir-Zeit-Benachrichtigung landet hier — als Flow, damit auch ein Tap auf die
    // schon laufende App (onNewIntent) ankommt und nicht nur der Kaltstart.
    private val deepLink = MutableStateFlow<SelliDeepLink?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Ergebnis egal:
            ohne Berechtigung überspringt der Hintergrund-Job den Benachrichtigungsschritt still. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dependencies = DefaultAppDependencies(this)
        // App-Öffnen ist ein guter Moment für frische Widget-Daten (MVP-Sync-Modell).
        WidgetRefreshScheduler.refreshNow(this)
        deepLink.value = intent?.toSelliDeepLink()
        requestNotificationPermissionIfNeeded()
        setContent {
            SelliTheme {
                val pendingDeepLink by deepLink.collectAsState()
                SelliApp(
                    dependencies = dependencies,
                    rememberOwnPerson = dependencies::rememberOwnPerson,
                    deepLink = pendingDeepLink,
                    onDeepLinkHandled = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toSelliDeepLink()?.let { target -> deepLink.value = target }
    }

    /**
     * Ab Android 13 ist `POST_NOTIFICATIONS` eine Laufzeit-Berechtigung. Bewusst erst hier und
     * nicht beim Verbinden: vorher sind Wir-Zeit-Benachrichtigungen noch nicht relevant.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Zwei mögliche Benachrichtigungsquellen hängen unterschiedliche Extras an denselben Intent:
 * [SharedEventNotifier] (Wir-Zeit-Termin) und [PartnerActivityNotifier] (neue Ausgabe/Idee).
 * Robust gegen fehlende/kaputte Extras (z. B. durch eine App-Aktualisierung) — dann einfach
 * kein Deep-Link statt Absturz.
 */
private fun Intent.toSelliDeepLink(): SelliDeepLink? =
    toEventDeepLink() ?: toPartnerActivityDeepLink()

private fun Intent.toEventDeepLink(): SelliDeepLink.Event? {
    val day = getStringExtra(SharedEventNotifier.EXTRA_DAY)
        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
        ?: return null
    val source = getStringExtra(SharedEventNotifier.EXTRA_SOURCE)
        ?.let { value -> runCatching { CalendarSource.valueOf(value) }.getOrNull() }
        ?: return null
    val eventId = getStringExtra(SharedEventNotifier.EXTRA_EVENT_ID)
        ?.takeUnless(String::isBlank)
        ?: return null

    return SelliDeepLink.Event(day = day, eventKey = EventKey(source = source, eventId = eventId))
}

private fun Intent.toPartnerActivityDeepLink(): SelliDeepLink? =
    when (getStringExtra(PartnerActivityNotifier.EXTRA_DESTINATION)) {
        PartnerActivityNotifier.DESTINATION_EXPENSES -> SelliDeepLink.Expenses
        PartnerActivityNotifier.DESTINATION_IDEEN ->
            getStringExtra(PartnerActivityNotifier.EXTRA_FOLDER_ID)
                ?.takeUnless(String::isBlank)
                ?.let { folderId -> SelliDeepLink.IdeenFolder(folderId) }
        else -> null
    }
