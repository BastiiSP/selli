package com.prehmus.selli.data.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
import com.prehmus.selli.domain.widget.NextPartnerEventSelector
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
            val events = mergeServiceFactory(applicationContext).mergedEvents(
                DateRange(
                    start = today,
                    endInclusive = today.plusDays(LOOKAHEAD_DAYS),
                ),
            )
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
            )

            WidgetSnapshotStore(applicationContext).save(snapshot)
            WidgetRuntime.onSnapshotUpdated?.invoke(applicationContext)

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
