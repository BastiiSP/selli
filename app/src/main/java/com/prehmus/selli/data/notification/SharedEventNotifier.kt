package com.prehmus.selli.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prehmus.selli.MainActivity
import com.prehmus.selli.R
import com.prehmus.selli.domain.notification.SharedEventChange
import com.prehmus.selli.domain.notification.SharedEventNotificationContent
import com.prehmus.selli.domain.notification.SharedEventNotificationFormatter

class SharedEventNotifier(
    private val context: Context,
    private val formatter: SharedEventNotificationFormatter = SharedEventNotificationFormatter(),
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    /** true, wenn Benachrichtigungen erlaubt sind — der Aufrufer überspringt sonst den ganzen Check. */
    fun areNotificationsEnabled(): Boolean = notificationManager.areNotificationsEnabled()

    fun notifyChanges(
        changes: List<SharedEventChange>,
        partnerDisplayName: String,
    ) {
        if (!areNotificationsEnabled()) {
            return
        }

        changes.forEach { change ->
            val content = formatter.format(change, partnerDisplayName)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_selli)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
                .setAutoCancel(true)
                .setContentIntent(contentIntent(change, content))
                .build()

            try {
                notificationManager.notify(content.notificationId, notification)
            } catch (_: SecurityException) {
                // Eine fehlende Laufzeit-Berechtigung darf den Hintergrund-Job nicht abbrechen.
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wir-Zeit-Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Neue oder geänderte gemeinsame Termine"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun contentIntent(
        change: SharedEventChange,
        content: SharedEventNotificationContent,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DAY, change.event.start.toLocalDate().toString())
            putExtra(EXTRA_SOURCE, change.event.source.name)
            putExtra(EXTRA_EVENT_ID, change.event.id)
        }

        return PendingIntent.getActivity(
            context,
            content.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "wir_zeit_updates"
        const val EXTRA_DAY = "selli_deeplink_day"
        const val EXTRA_SOURCE = "selli_deeplink_source"
        const val EXTRA_EVENT_ID = "selli_deeplink_event_id"
    }
}
