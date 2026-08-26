package com.prehmus.selli.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Gerätelokale Layout-Vorlieben für die Kalenderansicht — analog zur bestehenden
 * SharedPreferences-Persistenz der Google-Konten. Bewusst kein neues Persistenz-
 * Framework (kein DataStore-Gradle-Zusatz): eine kleine, synchron gelesene Ablage
 * reicht für zwei UI-Werte und bleibt vollständig im UI-Layer (Claude-Territorium).
 *
 * Zwei Stellschrauben, gemeinsam für alle drei Ansichten (Monat/Woche/Tag):
 *  - [calendarFraction]: Anteil des mittleren Bereichs, den Grid/Zeitstrahl bekommt
 *    (Rest geht an die Terminliste). Über das Zieh-Handle verstellbar, mit
 *    Mindestgrößen ([MIN_FRACTION]/[MAX_FRACTION]), damit kein Bereich verschwindet.
 *  - [headerCollapsed]: historisch — siehe dort.
 */
class LayoutPreferencesState(private val prefs: SharedPreferences) {

    var calendarFraction by mutableFloatStateOf(
        prefs.getFloat(KEY_FRACTION, DEFAULT_FRACTION).coerceIn(MIN_FRACTION, MAX_FRACTION),
    )
        private set

    /**
     * Historisch: Ein-/Ausklappen des früheren Kalender-Headers. Seit dem Umzug der
     * Countdown-Szene auf den Homescreen (26.08.2026) ohne Leser — bleibt nur erhalten,
     * damit auf den Geräten gespeicherte Werte nicht ins Leere laufen.
     */
    var headerCollapsed by mutableStateOf(prefs.getBoolean(KEY_HEADER_COLLAPSED, false))
        private set

    /** Verschiebt das Verhältnis live um [deltaFraction] (Zieh-Geste), gekappt auf die Grenzen. */
    fun nudgeCalendarFraction(deltaFraction: Float) {
        calendarFraction = (calendarFraction + deltaFraction).coerceIn(MIN_FRACTION, MAX_FRACTION)
    }

    /** Nach dem Loslassen dauerhaft sichern (nicht bei jedem Zieh-Delta schreiben). */
    fun persistCalendarFraction() {
        prefs.edit().putFloat(KEY_FRACTION, calendarFraction).apply()
    }

    fun updateHeaderCollapsed(collapsed: Boolean) {
        headerCollapsed = collapsed
        prefs.edit().putBoolean(KEY_HEADER_COLLAPSED, collapsed).apply()
    }

    companion object {
        const val MIN_FRACTION = 0.32f
        const val MAX_FRACTION = 0.78f

        // Ausgangsverhältnis entspricht dem bisherigen festen 1,3 : 1 zwischen Kalender und Liste.
        const val DEFAULT_FRACTION = 1.3f / 2.3f

        private const val KEY_FRACTION = "calendar_fraction"
        private const val KEY_HEADER_COLLAPSED = "header_collapsed"
    }
}

@Composable
fun rememberLayoutPreferences(): LayoutPreferencesState {
    val context = LocalContext.current
    return remember {
        LayoutPreferencesState(
            context.applicationContext.getSharedPreferences(
                "selli_layout",
                Context.MODE_PRIVATE,
            ),
        )
    }
}
