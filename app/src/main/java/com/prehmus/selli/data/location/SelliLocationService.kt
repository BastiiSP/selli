package com.prehmus.selli.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.prehmus.selli.MainActivity
import com.prehmus.selli.R
import com.prehmus.selli.data.logging.AndroidCalendarLogger
import com.prehmus.selli.data.supabase.SupabaseRuntime
import com.prehmus.selli.domain.location.LiveShare
import com.prehmus.selli.domain.location.SharingMode
import com.prehmus.selli.domain.location.cadence
import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.repository.OwnLiveShareState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Sparsamer Normalbetrieb mit ausdrücklich gestarteter, befristeter Live-Freigabe. */
class SelliLocationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: LiveShareStorage
    private var coordinator: LocationSharingCoordinator? = null
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private var expiryJob: Job? = null
    private var trackingMode: SharingMode? = null
    private var notificationShare: LiveShare? = null
    private var isStarting = false
    private var foregroundStarted = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        storage = LiveShareRuntime.storage(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        if (!hasFineLocationPermission()) {
            stopSelf()
            return
        }
        foregroundStarted = startLocationForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Auch null-Intents nach START_STICKY lesen den absoluten Ablauf aus dem Speicher.
        synchronized(storage) {
            if (intent?.action == ACTION_STOP_LIVE) {
                storage.write(null)
                LiveShareRuntime.mutableState.value = OwnLiveShareState()
            }
            val share = storage.read()
            if (LiveShareRuntime.mutableState.value.share != share) {
                LiveShareRuntime.mutableState.value = OwnLiveShareState(share = share)
            }
        }
        if (!foregroundStarted || !hasFineLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (coordinator != null) {
            refreshTracking()
            scheduleExpiry()
            syncRequests.trySend(Unit)
            return START_STICKY
        }
        if (isStarting) return START_STICKY

        val repositoryFactory = LocationRuntime.repositoryFactory
        val personProvider = LocationRuntime.ownPersonProvider
        if (repositoryFactory == null || personProvider == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        isStarting = true
        serviceScope.launch {
            try {
                val person = personProvider(applicationContext)
                if (person == null) {
                    stopSelf()
                    return@launch
                }
                val sharing = LocationSharingCoordinator(
                    repository = repositoryFactory(applicationContext),
                    person = person,
                    storage = storage,
                    state = LiveShareRuntime.mutableState,
                    elapsedMillis = SystemClock::elapsedRealtime,
                    logger = AndroidCalendarLogger,
                )
                coordinator = sharing
                // Ein Worker und ein Signal statt einer wachsenden Offline-Warteschlange.
                serviceScope.launch {
                    for (request in syncRequests) {
                        sharing.uploadLatest()
                    }
                }
                serviceScope.launch {
                    SupabaseRuntime.client?.accessState?.collect { state ->
                        if (state == CloudAccessState.CONNECTED && !sharing.isSyncing) {
                            syncRequests.trySend(Unit)
                        }
                    }
                }
                refreshTracking()
                scheduleExpiry()
                syncRequests.trySend(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AndroidCalendarLogger.error(SOURCE_RUNTIME, error)
                stopSelf()
            } finally {
                isStarting = false
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (::fusedLocationClient.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
        syncRequests.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (hasFineLocationPermission()) start(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleLocation(location: Location) {
        val sharing = coordinator ?: return
        // Zuerst ablaufen und die Anfrage umstellen, dann auch späte Fixes normal behandeln.
        refreshTracking()
        sharing.offer(
            LocationFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                measuredAt = Instant.ofEpochMilli(location.time),
            ),
        )
        syncRequests.trySend(Unit)
    }

    private fun refreshTracking() {
        val sharing = coordinator ?: return
        val share = sharing.refresh()
        val nextMode = sharing.mode
        if (nextMode != trackingMode) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            requestLocationUpdates(nextMode)
        }
        if (notificationShare != share) {
            notificationShare = share
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun scheduleExpiry() {
        expiryJob?.cancel()
        expiryJob = serviceScope.launch {
            while (isActive) {
                val share = coordinator?.refresh()
                val waitMillis = share?.remaining(Instant.now())?.toMillis()?.coerceIn(1L, 30_000L) ?: 30_000L
                delay(waitMillis)
                refreshTracking()
                syncRequests.trySend(Unit)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(mode: SharingMode) {
        if (!hasFineLocationPermission()) {
            stopSelf()
            return
        }
        val settings = mode.cadence()
        val priority = if (settings.highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, settings.intervalMillis)
            .setMinUpdateIntervalMillis(settings.minUpdateIntervalMillis)
            .setMinUpdateDistanceMeters(settings.minUpdateDistanceMeters)
            .setMaxUpdateDelayMillis(settings.maxUpdateDelayMillis)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
                    trackingMode = null
                    stopSelf()
                }
            trackingMode = mode
        } catch (error: SecurityException) {
            AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
            stopSelf()
        }
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Standortfreigabe", NotificationManager.IMPORTANCE_LOW)
            .apply {
                setSound(null, null)
                enableVibration(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startLocationForeground(): Boolean = try {
        notificationShare = storage.read()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (error: SecurityException) {
        AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
        stopSelf()
        false
    } catch (error: RuntimeException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && error is ForegroundServiceStartNotAllowedException) {
            AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
            stopSelf()
            false
        } else {
            throw error
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val share = notificationShare
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_selli)
            .setContentTitle(if (share == null) "Selli teilt deinen Standort" else "Live-Standort aktiv")
            .setContentText(
                if (share == null) {
                    "Alle 15 Min., sparsam"
                } else {
                    "Endet um ${DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(share.endsAt)}"
                },
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
        if (share != null) {
            val stopIntent = PendingIntent.getService(
                this,
                NOTIFICATION_ID,
                Intent(this, SelliLocationService::class.java).setAction(ACTION_STOP_LIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Beenden", stopIntent)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_SYNC = "com.prehmus.selli.location.SYNC"
        const val ACTION_STOP_LIVE = "com.prehmus.selli.location.STOP_LIVE"
        private const val NOTIFICATION_CHANNEL_ID = "selli_location"
        private const val NOTIFICATION_ID = 2_026_0826
        private const val SOURCE_RUNTIME = "Standortdienst-Abhängigkeiten"
        private const val SOURCE_PERMISSION = "Standortdienst-Berechtigung"

        fun start(context: Context) = startWithAction(context, ACTION_SYNC)

        fun sync(context: Context) = startWithAction(context, ACTION_SYNC)

        private fun startWithAction(context: Context, action: String) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SelliLocationService::class.java).setAction(action),
                )
            } catch (error: SecurityException) {
                AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
            } catch (error: RuntimeException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && error is ForegroundServiceStartNotAllowedException) {
                    AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
                } else {
                    throw error
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SelliLocationService::class.java))
        }
    }
}
