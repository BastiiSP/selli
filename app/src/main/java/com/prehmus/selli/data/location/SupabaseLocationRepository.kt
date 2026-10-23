package com.prehmus.selli.data.location

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.data.supabase.classifyCloudError
import com.prehmus.selli.data.supabase.logCloudError
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.LocationRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SupabaseLocationRepository(
    private val client: SelliSupabaseClient,
    private val ownPerson: () -> Person,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : LocationRepository {
    private val feed = ResilientLocationFeed(object : LocationFeedSource {
        override val isConfigured: Boolean get() = client.isConfigured
        override val accessState get() = client.accessState
        override suspend fun load(): Result<List<PersonLocation>> = client.withSession { supabase ->
            supabase.from(LOCATIONS_TABLE).select().decodeList<LocationRow>().mapNotNull(LocationRow::toDomain)
        }
        override fun changes(): Flow<Result<Unit>> = realtimeChanges()
    })

    override fun observeLocationFeed(): Flow<LocationFeedEvent> = feed.observe()

    private fun realtimeChanges(): Flow<Result<Unit>> = channelFlow {
        val supabase = client.client ?: return@channelFlow
        // Mehrere Sammler dürfen sich beim Abmelden nicht gegenseitig ihren Kanal entfernen.
        val channel = supabase.channel("selli-locations-${UUID.randomUUID()}")
        try {
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = LOCATIONS_TABLE
            }
            val changesJob = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    changes.collect { send(Result.success(Unit)) }
                    throw IllegalStateException("Standort-Realtime wurde beendet.")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    logCloudError(logger, SOURCE_REALTIME, error)
                    send(Result.failure(classifyCloudError(error)))
                    // join unten bereinigt den Kanal; der Feed pollt und abonniert erneut.
                }
            }
            try {
                channel.subscribe()
                changesJob.join()
            } finally {
                changesJob.cancel()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logCloudError(logger, SOURCE_REALTIME, error)
            send(Result.failure(classifyCloudError(error)))
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(5_000L) {
                    try {
                        supabase.realtime.removeChannel(channel)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        logCloudError(logger, SOURCE_REALTIME, error)
                    }
                }
            }
        }
    }

    override suspend fun publishOwnLocation(location: PersonLocation): Result<Unit> = client.withSession { supabase ->
        val userId = client.currentUserId() ?: throw CloudSyncException.ReconfirmationRequired()
        val row = LocationRow.from(location.copy(person = ownPerson()), userId)
        supabase.from(LOCATIONS_TABLE).upsert(row) { onConflict = "user_id" }
        Unit
    }

    private companion object {
        const val LOCATIONS_TABLE = "locations"
        const val SOURCE_REALTIME = "Supabase-Standort-Realtime"
    }
}
