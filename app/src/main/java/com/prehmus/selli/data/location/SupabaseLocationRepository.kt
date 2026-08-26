package com.prehmus.selli.data.location

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.LocationRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SupabaseLocationRepository(
    private val client: SelliSupabaseClient,
    private val ownPerson: () -> Person,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : LocationRepository {
    override fun observeLocations(): Flow<List<PersonLocation>> = channelFlow {
        if (!client.isConfigured) {
            send(emptyList())
            return@channelFlow
        }

        val signInResult = client.ensureSignedIn()
        if (signInResult.isFailure) {
            signInResult.exceptionOrNull()?.let { logError(SOURCE_AUTH, it) }
            send(emptyList())
            return@channelFlow
        }

        val supabase = client.client
        if (supabase == null) {
            send(emptyList())
            return@channelFlow
        }

        suspend fun loadLocations(): List<PersonLocation> =
            supabase.from(LOCATIONS_TABLE)
                .select()
                .decodeList<LocationRow>()
                .mapNotNull(LocationRow::toDomain)

        try {
            send(loadLocations())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_SELECT, error)
            send(emptyList())
        }

        suspend fun pollLocations() {
            while (isActive) {
                delay(POLLING_INTERVAL_MILLIS)
                try {
                    send(loadLocations())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    logError(SOURCE_POLLING, error)
                }
            }
        }

        val realtimeChannel = try {
            supabase.channel(REALTIME_CHANNEL)
        } catch (error: Throwable) {
            logError(SOURCE_REALTIME, error)
            pollLocations()
            return@channelFlow
        }
        val changes = try {
            realtimeChannel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
                table = LOCATIONS_TABLE
            }
        } catch (error: Throwable) {
            logError(SOURCE_REALTIME, error)
            pollLocations()
            return@channelFlow
        }
        var fallbackJob: Job? = null
        val changesJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                changes.collect {
                    send(loadLocations())
                }
                error("Der Supabase-Realtime-Flow wurde unerwartet beendet.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logError(SOURCE_REALTIME, error)
                fallbackJob = launch { pollLocations() }
            }
        }

        try {
            realtimeChannel.subscribe()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_REALTIME, error)
            changesJob.cancel()
            fallbackJob = launch { pollLocations() }
        }

        try {
            awaitClose {
                changesJob.cancel()
                fallbackJob?.cancel()
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { realtimeChannel.unsubscribe() }
                    .onFailure { error -> logError(SOURCE_REALTIME, error) }
            }
        }
    }

    override suspend fun publishOwnLocation(location: PersonLocation): Result<Unit> {
        return try {
            client.ensureSignedIn().getOrThrow()
            val userId = client.currentUserId()
                ?: throw IllegalStateException("Keine Supabase-Benutzersitzung verfügbar.")
            val supabase = client.client
                ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
            val row = LocationRow.from(
                location = location.copy(person = ownPerson()),
                userId = userId,
            )

            supabase.from(LOCATIONS_TABLE).upsert(row) {
                onConflict = USER_ID_COLUMN
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            // Nicht in ein Result.failure verwandeln: das Abbrechen des Service-Scopes muss
            // nach oben durchschlagen, sonst laufen Coroutinen nach onDestroy weiter.
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_PUBLISH, error)
            Result.failure(error)
        }
    }

    private fun logError(source: String, error: Throwable) {
        runCatching { logger.error(source, error) }
    }

    private companion object {
        const val LOCATIONS_TABLE = "locations"
        const val USER_ID_COLUMN = "user_id"
        const val PUBLIC_SCHEMA = "public"
        const val REALTIME_CHANNEL = "selli-locations"
        const val POLLING_INTERVAL_MILLIS = 60_000L
        const val SOURCE_AUTH = "Supabase-Standort-Anmeldung"
        const val SOURCE_SELECT = "Supabase-Standorte laden"
        const val SOURCE_REALTIME = "Supabase-Standort-Realtime"
        const val SOURCE_POLLING = "Supabase-Standort-Polling"
        const val SOURCE_PUBLISH = "Supabase-Standort-Upload"
    }
}
