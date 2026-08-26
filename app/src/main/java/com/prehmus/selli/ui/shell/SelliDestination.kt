package com.prehmus.selli.ui.shell

/**
 * Die drei gleichwertigen Ziele der Bottom-Navigation. Die Reihenfolge der Einträge ist
 * zugleich die Reihenfolge in der Leiste — „Wir" sitzt bewusst in der Mitte, weil es das
 * zentrale „auf einen Blick"-Ziel ist.
 */
enum class SelliDestination(val route: String, val label: String) {
    CALENDAR(route = "calendar", label = "Kalender"),
    HOME(route = "home", label = "Wir"),
    LOCATION(route = "location", label = "Standort"),
    ;

    companion object {
        /**
         * Ziel zur Route, oder `null` für unbekannte Routen und für die Einstellungen —
         * die sind bewusst kein Bottom-Navigation-Ziel.
         */
        fun fromRoute(route: String?): SelliDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}

/** Profil-/Einstellungsbereich: eigener Vollbild-Screen über dem Bottom-Navigation-Gerüst. */
const val SETTINGS_ROUTE = "settings"

/** Ziel, mit dem die App startet — „Wir" ist der zentrale „auf einen Blick"-Bildschirm. */
val SelliStartDestination: SelliDestination = SelliDestination.HOME
