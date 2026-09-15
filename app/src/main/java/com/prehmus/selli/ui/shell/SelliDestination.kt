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
         * Ziel zur Route, oder `null` für unbekannte Routen und für die Einstellungen —
         * die sind bewusst kein Bottom-Navigation-Ziel.
         *
         * Die Ordner-Route zählt als „Ideen": sie zeigt dieselbe Übersicht mit einem
         * aufgeklappten Ordner, also muss die Leiste auch „Ideen" hervorheben.
         */
        fun fromRoute(route: String?): SelliDestination? = when (route) {
            IDEEN_FOLDER_ROUTE -> IDEEN
            else -> entries.firstOrNull { destination -> destination.route == route }
        }
    }
}

/** Profil-/Einstellungsbereich: eigener Vollbild-Screen über dem Bottom-Navigation-Gerüst. */
const val SETTINGS_ROUTE = "settings"

/**
 * Einsprungpunkt auf einen bestimmten Ordner. Seit dem 15.09.2026 gibt es dafür keine eigene
 * Unterseite mehr — die Route zeigt dieselbe Ideen-Übersicht wie [SelliDestination.IDEEN],
 * nur mit dem genannten Ordner aufgeklappt (siehe `IdeenScreen.initialExpandedFolderId`).
 * Gedacht für Navigation von außen, etwa die „Neue Idee"-Benachrichtigung, die die Ordner-ID
 * bereits als Extra mitschickt. Der Ordnername wird bewusst nicht als Argument mitgegeben,
 * weil er URL-unsichere Zeichen enthalten kann.
 */
const val IDEEN_FOLDER_ROUTE = "ideen/{folderId}"

fun ideenFolderRoute(folderId: String): String = "ideen/$folderId"

/** Ziel, mit dem die App startet — „Wir" ist der zentrale „auf einen Blick"-Bildschirm. */
val SelliStartDestination: SelliDestination = SelliDestination.HOME
