package com.prehmus.selli.data.google

import android.accounts.Account as AndroidAccount
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes

interface GoogleCalendarServiceFactory {
    fun create(accountEmail: String): Calendar
}

internal data class GoogleCalendarAccount(
    val name: String,
    val type: String = GOOGLE_ACCOUNT_TYPE,
) {
    fun toAndroidAccount(): AndroidAccount = AndroidAccount(name, type)
}

internal interface GoogleCalendarCredential {
    fun setSelectedAccount(account: GoogleCalendarAccount)
}

internal object GoogleCalendarAccountSelector {
    fun select(
        credential: GoogleCalendarCredential,
        accountEmail: String,
    ) {
        // Do not use selectedAccountName: it performs an AccountManager lookup, which can return
        // null for Credential Manager sign-ins on Android 8+ when the app cannot see Google accounts.
        credential.setSelectedAccount(GoogleCalendarAccount(name = accountEmail))
    }
}

private class AndroidGoogleCalendarCredential(
    private val credential: GoogleAccountCredential,
) : GoogleCalendarCredential {
    override fun setSelectedAccount(account: GoogleCalendarAccount) {
        credential.selectedAccount = account.toAndroidAccount()
    }
}

class AndroidGoogleCalendarServiceFactory(
    context: Context,
    private val applicationName: String = "Selli",
) : GoogleCalendarServiceFactory {
    private val applicationContext = context.applicationContext

    override fun create(accountEmail: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            applicationContext,
            listOf(CalendarScopes.CALENDAR),
        )
        GoogleCalendarAccountSelector.select(
            credential = AndroidGoogleCalendarCredential(credential),
            accountEmail = accountEmail,
        )

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(applicationName)
            .build()
    }
}

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
