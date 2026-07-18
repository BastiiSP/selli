package com.prehmus.selli.data.google

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes

interface GoogleCalendarServiceFactory {
    fun create(accountEmail: String): Calendar
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
        ).apply {
            selectedAccountName = accountEmail
        }

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(applicationName)
            .build()
    }
}
