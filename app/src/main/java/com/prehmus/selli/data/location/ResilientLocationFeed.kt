package com.prehmus.selli.data.location

import com.prehmus.selli.data.supabase.classifyCloudError
import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.PersonLocation
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Kleiner Port für Wiederanlauf-Tests ohne Android oder Websocket. */
internal interface LocationFeedSource {
    val isConfigured: Boolean
    val accessState: StateFlow<CloudAccessState>
    suspend fun load(): Result<List<PersonLocation>>
    fun changes(): Flow<Result<Unit>>
}

internal class ResilientLocationFeed(
    private val source: LocationFeedSource,
    private val pollingMillis: Long = 60_000L,
    private val retryMillis: List<Long> = listOf(5_000L, 15_000L, 30_000L, 60_000L),
) {
    fun observe(): Flow<LocationFeedEvent> = channelFlow {
        if (!source.isConfigured) {
            send(LocationFeedEvent.Failed(CloudSyncException.NotConfigured()))
            return@channelFlow
        }
        val wakeups = Channel<Unit>(Channel.CONFLATED)
        val reconnected = AtomicBoolean(false)
        var realtimeJob: Job? = null
        val loading = AtomicBoolean(false)
        val connectionJob = launch(start = CoroutineStart.UNDISPATCHED) {
            var previous = source.accessState.value
            source.accessState.collect { state ->
                // Während eines bereits laufenden Versuchs kein zweiter durch dessen eigenen
                // ensureSignedIn()-Erfolg: sonst würde ein PostgREST-Netzfehler den Backoff umgehen.
                if (state == CloudAccessState.CONNECTED && previous != state && !loading.get()) {
                    reconnected.set(true)
                    wakeups.trySend(Unit)
                }
                previous = state
            }
        }
        var failures = 0
        try {
            while (isActive) {
                loading.set(true)
                val result = try {
                    source.load()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(classifyCloudError(error))
                } finally {
                    loading.set(false)
                }
                val error = result.exceptionOrNull()
                val waitMillis = if (error == null) {
                    send(LocationFeedEvent.Loaded(result.getOrThrow()))
                    failures = 0
                    if (realtimeJob?.isActive != true) {
                        realtimeJob = launch {
                            try {
                                source.changes().collect { change ->
                                    change.fold(
                                        onSuccess = { wakeups.trySend(Unit) },
                                        onFailure = { send(LocationFeedEvent.Failed(classifyCloudError(it))) },
                                    )
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                send(LocationFeedEvent.Failed(classifyCloudError(error)))
                            }
                        }
                    }
                    // Polling bleibt auch bei still abgerissenem Websocket aktiv.
                    pollingMillis
                } else {
                    send(LocationFeedEvent.Failed(classifyCloudError(error)))
                    retryMillis[failures].also { failures = (failures + 1).coerceAtMost(retryMillis.lastIndex) }
                }
                withTimeoutOrNull(waitMillis) { wakeups.receive() }
                if (reconnected.getAndSet(false)) {
                    realtimeJob?.cancelAndJoin()
                    realtimeJob = null
                    failures = 0
                }
            }
        } finally {
            connectionJob.cancel()
            realtimeJob?.cancel()
            wakeups.close()
        }
    }
}
