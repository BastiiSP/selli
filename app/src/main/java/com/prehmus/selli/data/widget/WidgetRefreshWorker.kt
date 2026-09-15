package com.prehmus.selli.data.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prehmus.selli.data.notification.DeleteRequestNotificationStore
import com.prehmus.selli.data.notification.ExpenseSeenStore
import com.prehmus.selli.data.notification.PartnerActivityNotifier
import com.prehmus.selli.data.notification.SharedEventFingerprintStore
import com.prehmus.selli.data.notification.SharedEventNotifier
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
import com.prehmus.selli.domain.model.other
import com.prehmus.selli.domain.notification.PartnerSharedEventChangeDetector
import com.prehmus.selli.domain.notification.SharedEventDeletionRequestDetector
import com.prehmus.selli.domain.notification.detectNewExpenses
import com.prehmus.selli.domain.widget.NextPartnerEventSelector
import com.prehmus.selli.domain.widget.NextSharedEventSelector
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val partner = loadPartnerInfo() ?: return Result.success()
        val mergeServiceFactory = WidgetRuntime.mergeServiceFactory ?: return Result.success()

        return try {
            val today = LocalDate.now()
            val now = LocalDateTime.now()
            val range = DateRange(start = today, endInclusive = today.plusDays(LOOKAHEAD_DAYS))
            // Ein Fetch für alles: Zeile 1 (nächster Partnertermin), Zeile 2 und 3
            // (nächste Wir-Zeit plus Countdown) und die Benachrichtigungs-Erkennung teilen sich
            // dieselbe Terminliste — kein zusätzlicher Request an Google oder die ICS-Feeds.
            val events = mergeServiceFactory(applicationContext).mergedEvents(range)
            val nextEvent = NextPartnerEventSelector().select(
                events = events,
                partner = partner.person,
                now = now,
            )
            val snapshot = WidgetSnapshot(
                partnerPerson = partner.person,
                partnerDisplayName = partner.displayName,
                nextEvent = nextEvent,
                updatedAt = now,
                nextSharedEvent = NextSharedEventSelector().select(events = events, now = now),
            )

            WidgetSnapshotStore(applicationContext).save(snapshot)
            WidgetRuntime.onSnapshotUpdated?.invoke(applicationContext)

            // Nach dem Snapshot und bewusst fehlertolerant: eine Panne beim Benachrichtigen darf
            // weder das frische Widget kosten noch über Result.retry() zu Doppel-Meldungen führen.
            runCatching { notifySharedEventChanges(events = events, partner = partner) }
            runCatching { notifyNewExpenses(partner) }

            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }

    /**
     * Wir-Zeit-Änderungen des Partners melden. Sind Benachrichtigungen aus, wird der komplette
     * Schritt übersprungen — auch das Mitschreiben des gesehenen Stands. Sonst würden Termine aus
     * dieser Phase still als "bereits gesehen" gelten und nach dem Erteilen der Berechtigung nie
     * nachgemeldet.
     */
    private fun notifySharedEventChanges(events: List<CalendarEvent>, partner: PartnerInfo) {
        val notifier = SharedEventNotifier(applicationContext)
        if (!notifier.areNotificationsEnabled()) return

        val fingerprintStore = SharedEventFingerprintStore(applicationContext)
        val fingerprintResult = PartnerSharedEventChangeDetector().detect(
            currentEvents = events,
            partner = partner.person,
            previouslySeen = fingerprintStore.load(),
        )

        val deleteRequestStore = DeleteRequestNotificationStore(applicationContext)
        val deleteRequestResult = SharedEventDeletionRequestDetector().detect(
            currentEvents = events,
            self = partner.person.other(),
            alreadyNotified = deleteRequestStore.load(),
        )

        notifier.notifyChanges(fingerprintResult.changes + deleteRequestResult.changes, partner.displayName)
        fingerprintStore.save(fingerprintResult.updatedFingerprints)
        deleteRequestStore.save(deleteRequestResult.updatedNotified)
    }

    private suspend fun notifyNewExpenses(partner: PartnerInfo) {
        val repositoryFactory = WidgetRuntime.expenseRepositoryFactory ?: return
        val notifier = PartnerActivityNotifier(applicationContext)
        if (!notifier.areNotificationsEnabled()) return

        val expenses = repositoryFactory().loadExpenses()
        val store = ExpenseSeenStore(applicationContext)
        val result = detectNewExpenses(
            currentExpenses = expenses,
            self = partner.person.other(),
            alreadySeenIds = store.load(),
        )
        result.newExpenses.forEach { expense -> notifier.notifyNewExpense(expense, partner.displayName) }
        store.save(result.updatedSeenIds)
    }

    private fun loadPartnerInfo(): PartnerInfo? {
        val preferences = applicationContext.getSharedPreferences(
            GOOGLE_CALENDAR_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val personName = preferences
            .getString(KEY_PARTNER_PERSON, null)
            ?.takeUnless(String::isBlank)
            ?: return null
        val displayName = preferences
            .getString(KEY_PARTNER_DISPLAY_NAME, null)
            ?.takeUnless(String::isBlank)
            ?: return null
        val person = runCatching { Person.valueOf(personName) }.getOrNull() ?: return null

        return PartnerInfo(person = person, displayName = displayName)
    }

    private data class PartnerInfo(
        val person: Person,
        val displayName: String,
    )

    private companion object {
        const val GOOGLE_CALENDAR_PREFS_NAME = "selli_google_calendar"
        const val KEY_PARTNER_PERSON = "partner_person"
        const val KEY_PARTNER_DISPLAY_NAME = "partner_display_name"
        const val LOOKAHEAD_DAYS = 30L
        const val MAX_RETRY_ATTEMPTS = 2
    }
}
