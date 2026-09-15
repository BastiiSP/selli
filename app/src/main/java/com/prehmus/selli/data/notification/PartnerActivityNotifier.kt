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
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.NoteItem

/**
 * Benachrichtigt über neue Ausgaben und neue Ideen-Punkte des Partners — eigener,
 * schlanker Kanal statt Wiederverwendung von `SharedEventNotifier`, dessen Vertrag
 * (`SharedEventChange`, Tag/Termin-Deep-Link) auf Kalender-Termine zugeschnitten ist.
 */
class PartnerActivityNotifier(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    fun areNotificationsEnabled(): Boolean = notificationManager.areNotificationsEnabled()

    fun notifyNewExpense(expense: Expense, partnerDisplayName: String) {
        if (!areNotificationsEnabled()) return
        val amountText = "%.2f".format(expense.amount).replace('.', ',')
        notify(
            notificationId = EXPENSE_NOTIFICATION_ID_OFFSET + expense.id.hashCode(),
            title = "Neue Ausgabe",
            text = "$partnerDisplayName hat $amountText € für \"${expense.description}\" eingetragen",
            intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_DESTINATION, DESTINATION_EXPENSES)
            },
        )
    }

    fun notifyNewNoteItem(item: NoteItem, folderName: String, partnerDisplayName: String) {
        if (!areNotificationsEnabled()) return
        notify(
            notificationId = NOTE_NOTIFICATION_ID_OFFSET + item.id.hashCode(),
            title = "Neue Idee",
            text = "$partnerDisplayName hat \"${item.text}\" zu $folderName hinzugefügt",
            intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_DESTINATION, DESTINATION_IDEEN)
                putExtra(EXTRA_FOLDER_ID, item.folderId)
            },
        )
    }

    private fun notify(notificationId: Int, title: String, text: String, intent: Intent) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_selli)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Fehlende Laufzeit-Berechtigung darf den Hintergrund-Job nicht abbrechen.
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Selli-Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Neue gemeinsame Ausgaben und Ideen"
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "selli_activity_updates"
        const val EXTRA_DESTINATION = "selli_deeplink_destination"
        const val EXTRA_FOLDER_ID = "selli_deeplink_folder_id"
        const val DESTINATION_EXPENSES = "expenses"
        const val DESTINATION_IDEEN = "ideen"
        private const val EXPENSE_NOTIFICATION_ID_OFFSET = 20_000_000
        private const val NOTE_NOTIFICATION_ID_OFFSET = 30_000_000
    }
}
