package com.prehmus.selli.ui.shell

/**
 * Die fünf gleichwertigen Ziele der Bottom-Navigation. Die Reihenfolge der Einträge ist
 * zugleich die Reihenfolge in der Leiste — „Wir" sitzt bewusst in der Mitte, weil es das
 * zentrale „auf einen Blick"-Ziel ist.
 */
enum class SelliDestination(val route: String, val label: String) {
    CALENDAR(route = "calendar", label = "Kalender"),
    IDEEN(route = "ideen", label = "Ideen"),
    HOME(route = "home", label = "Wir"),
    EXPENSES(route = "expenses", label = "Kosten"),
    LOCATION(route = "location", label = "Standort"),
    ;

    companion object {
        /**
         * Ziel zur Route, oder `null` für unbekannte Routen und für Einstellungen/
         * Ordner-Detail — die sind bewusst keine Bottom-Navigation-Ziele.
         */
        fun fromRoute(route: String?): SelliDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}

/** Profil-/Einstellungsbereich: eigener Vollbild-Screen über dem Bottom-Navigation-Gerüst. */
const val SETTINGS_ROUTE = "settings"

/**
 * Ordner-Detailansicht (Ebene 2 von "Ideen"): wie [SETTINGS_ROUTE] ein Vollbild-Screen ohne
 * globale Top-/Bottom-Bar, mit eigenem Header (siehe `FolderDetailScreen`). Der Ordnername
 * wird bewusst nicht als Argument mitgegeben (URL-unsichere Zeichen möglich) — siehe
 * `FolderDetailViewModel.refresh()`.
 */
const val IDEEN_FOLDER_ROUTE = "ideen/{folderId}"

fun ideenFolderRoute(folderId: String): String = "ideen/$folderId"

/** Ziel, mit dem die App startet — „Wir" ist der zentrale „auf einen Blick"-Bildschirm. */
val SelliStartDestination: SelliDestination = SelliDestination.HOME
