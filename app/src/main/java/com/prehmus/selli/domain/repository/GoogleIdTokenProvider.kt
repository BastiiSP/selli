package com.prehmus.selli.domain.repository

interface GoogleIdTokenProvider {
    fun lastGoogleIdToken(): String?
    /** Frisches Google-ID-Token für das gespeicherte eigene Konto, ohne jede UI. Auch für Hintergrundaufgaben. */
    suspend fun freshGoogleIdTokenSilently(): Result<String>
}

/** Nur im Activity-Graph verfügbar. Zeigt ggf. den Google-Kontodialog. */
interface InteractiveGoogleIdTokenProvider {
    suspend fun reconfirmOwnGoogleAccount(): GoogleReconfirmation
}

sealed interface GoogleReconfirmation {
    data class Success(val idToken: String) : GoogleReconfirmation
    data object Cancelled : GoogleReconfirmation
    data class WrongAccount(val expectedEmail: String) : GoogleReconfirmation
    data class Failed(val cause: Throwable) : GoogleReconfirmation
}
