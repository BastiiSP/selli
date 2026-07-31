package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.key
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SharedEventNotificationContent(
    val title: String,
    val text: String,
    /** Stabil pro Termin: mehrere Änderungen am selben Termin ersetzen die Benachrichtigung. */
    val notificationId: Int,
)

class SharedEventNotificationFormatter {
    fun format(
        change: SharedEventChange,
        partnerDisplayName: String,
    ): SharedEventNotificationContent {
        val title: String
        val text: String

        when (change) {
            is SharedEventChange.New -> {
                title = "$partnerDisplayName hat gemeinsame Zeit eingetragen"
                val date = change.event.start.toLocalDate().format(DATE_FORMATTER)
                text = "${change.event.title} – $date"
            }

            is SharedEventChange.Updated -> {
                title = "$partnerDisplayName hat euren gemeinsamen Termin aktualisiert"
                text = "${change.event.title} – ${change.changedFields.joinToString(", ")}"
            }
        }

        return SharedEventNotificationContent(
            title = title,
            text = text,
            notificationId = change.event.key().hashCode(),
        )
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMAN)
    }
}
