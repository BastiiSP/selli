package com.prehmus.selli.data.location

import com.prehmus.selli.data.supabase.classifyCloudError
import com.prehmus.selli.data.supabase.logCloudError
import com.prehmus.selli.domain.location.LiveShare
import com.prehmus.selli.domain.location.SharingMode
import com.prehmus.selli.domain.location.sharingModeAt
import com.prehmus.selli.domain.location.shouldUploadFix
import com.prehmus.selli.domain.location.validAt
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.LocationRepository
import com.prehmus.selli.domain.repository.OwnLiveShareState
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val measuredAt: Instant,
)

/** Lokale Änderungen warten nie auf das Netz; alle Schreibzugriffe teilen sich einen Mutex. */
internal class LocationSharingCoordinator(
    private val repository: LocationRepository,
    private val person: Person,
    private val storage: LiveShareStorage,
    private val state: MutableStateFlow<OwnLiveShareState>,
    private val clock: Clock = Clock.systemUTC(),
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val logger: CalendarLogger = NoOpCalendarLogger,
) {
    private val networkMutex = Mutex()
    private val networkInProgress = AtomicBoolean(false)
    private val pending = AtomicReference<LocationFix?>(null)
    private var lastUploaded: LocationFix? = null
    private var lastUploadMillis: Long? = null
    private var lastUploadShare: LiveShare? = null
    private var publishedUntil: Instant? = null
    private var publicationKnown = false

    val isSyncing: Boolean get() = networkInProgress.get()
    val mode: SharingMode get() = sharingModeAt(refresh(), clock.instant())

    init {
        synchronized(storage) {
            state.value = OwnLiveShareState(share = readValidShare())
        }
    }

    fun refresh(): LiveShare? = synchronized(storage) {
        readValidShare().also { share ->
            if (state.value.share != share) state.value = OwnLiveShareState(share = share)
        }
    }

    fun stop() {
        synchronized(storage) {
            storage.write(null)
            state.value = OwnLiveShareState()
        }
    }

    fun offer(fix: LocationFix) {
        refresh()
        pending.updateAndGet { previous ->
            if (previous == null || previous.measuredAt <= fix.measuredAt) fix else previous
        }
    }

    suspend fun uploadLatest() = serialized {
        val fix = pending.get()
        if (fix == null) {
            reconcileLocked()
            return@serialized
        }
        val share = refresh()
        val currentMode = sharingModeAt(share, clock.instant())
        val previous = lastUploaded.takeIf { lastUploadShare == share }
        val sinceUpload = lastUploadMillis?.takeIf { previous != null }?.let {
            (elapsedMillis() - it).coerceAtLeast(0L)
        }
        if (!shouldUploadFix(currentMode, sinceUpload, previous?.let { distanceMeters(it, fix) }, fix.accuracyMeters)) {
            pending.compareAndSet(fix, null)
            reconcileLocked()
            return@serialized
        }
        val location = PersonLocation(
            person = person,
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyMeters = fix.accuracyMeters?.toDouble(),
            speedMetersPerSecond = fix.speedMetersPerSecond?.toDouble(),
            isMoving = currentMode == SharingMode.LIVE && (fix.speedMetersPerSecond ?: 0f) > 2.5f,
            updatedAt = fix.measuredAt,
        )
        cloudResult { repository.publishOwnLocation(location) }.fold(
            onSuccess = {
                lastUploaded = fix
                lastUploadMillis = elapsedMillis()
                lastUploadShare = share
                pending.compareAndSet(fix, null)
            },
            onFailure = { error -> recordError(error) },
        )
        // Auch bei einem fehlgeschlagenen Positions-Upload darf ein Stopp die Endzeit löschen.
        reconcileLocked()
    }

    suspend fun reconcile() = serialized { reconcileLocked() }

    private suspend fun serialized(block: suspend () -> Unit) {
        networkMutex.withLock {
            networkInProgress.set(true)
            try {
                block()
            } finally {
                networkInProgress.set(false)
            }
        }
    }

    private suspend fun reconcileLocked() {
        while (true) {
            // Erst nach Erwerb des Mutex lesen, keine in einer Coroutine eingefrorene Endzeit senden.
            val desired = refresh()?.endsAt
            if (publicationKnown && publishedUntil == desired) {
                publishState()
                return
            }
            val result = cloudResult { repository.publishLiveUntil(desired) }
            val error = result.exceptionOrNull()
            if (error == null) {
                publicationKnown = result.getOrThrow()
                if (publicationKnown) publishedUntil = desired
                publishState()
            } else {
                // Der Server könnte trotz verlorener Antwort geschrieben haben.
                publicationKnown = false
                recordError(error)
            }
            // Stopp während eines Requests wird unmittelbar danach abgeglichen, auch bei Netzfehlern.
            if (refresh()?.endsAt == desired) return
        }
    }

    private fun publishState() = synchronized(storage) {
        val share = readValidShare()
        state.value = OwnLiveShareState(
            share = share,
            published = publicationKnown && publishedUntil == share?.endsAt,
        )
    }

    private fun recordError(error: Throwable) {
        val classified = classifyCloudError(error)
        logCloudError(logger, "Standortdienst-Abgleich", classified)
        synchronized(storage) {
            val share = readValidShare()
            state.value = OwnLiveShareState(
                share = share,
                published = publicationKnown && publishedUntil == share?.endsAt,
                syncError = classified,
            )
        }
    }

    private fun readValidShare(): LiveShare? {
        val stored = storage.read()
        val valid = stored.validAt(clock.instant())
        if (stored != null && valid == null) storage.write(null)
        return valid
    }

    private suspend fun <T> cloudResult(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(classifyCloudError(error))
    }
}

private fun distanceMeters(from: LocationFix, to: LocationFix): Float {
    val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
    val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    val bounded = a.coerceIn(0.0, 1.0)
    return (6_371_000.0 * 2 * atan2(sqrt(bounded), sqrt(1 - bounded))).toFloat()
}
