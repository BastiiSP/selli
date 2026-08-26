package com.prehmus.selli.data.location

import android.Manifest
import android.annotation.SuppressLint
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
import com.prehmus.selli.domain.location.MOVING_DISTANCE_THRESHOLD_METERS
import com.prehmus.selli.domain.location.MOVING_SPEED_THRESHOLD_MPS
import com.prehmus.selli.domain.location.TrackingMode
import com.prehmus.selli.domain.location.nextTrackingMode
import com.prehmus.selli.domain.location.settings
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.repository.LocationRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Erfasst den eigenen Standort mit bewegungsabhängiger Taktung und lädt ihn fehlertolerant hoch. */
class SelliLocationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationRepository: LocationRepository? = null
    private var ownPerson: Person? = null
    private var trackingMode = TrackingMode.STATIONARY
    private var lastMovementElapsedRealtime = 0L
    private val uploadMutex = Mutex()

    @Volatile
    private var lastUploadedLocation: Location? = null

    private var isStarting = false
    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        if (!hasFineLocationPermission()) {
            stopSelf()
            return
        }
        startLocationForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasFineLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (isStarting || isTracking) return START_STICKY

        val repositoryFactory = LocationRuntime.repositoryFactory
        val personProvider = LocationRuntime.ownPersonProvider
        if (repositoryFactory == null || personProvider == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        isStarting = true
        serviceScope.launch {
            val resolvedPerson = runCatching { personProvider(applicationContext) }
                .onFailure { error -> AndroidCalendarLogger.error(SOURCE_RUNTIME, error) }
                .getOrNull()
            if (resolvedPerson == null) {
                stopSelf()
                return@launch
            }

            val repository = runCatching { repositoryFactory(applicationContext) }
                .onFailure { error -> AndroidCalendarLogger.error(SOURCE_RUNTIME, error) }
                .getOrNull()
            if (repository == null) {
                stopSelf()
                return@launch
            }

            ownPerson = resolvedPerson
            locationRepository = repository
            beginTrackingIfPermitted()
            isStarting = false
        }

        return START_STICKY
    }

    override fun onDestroy() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        isStarting = false
        isTracking = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleLocation(location: Location) {
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val metersSinceLastUpload = lastUploadedLocation?.distanceTo(location) ?: 0f
        val speedMetersPerSecond = if (location.hasSpeed()) location.speed else null
        val movementDetected =
            speedMetersPerSecond != null && speedMetersPerSecond > MOVING_SPEED_THRESHOLD_MPS ||
                metersSinceLastUpload > MOVING_DISTANCE_THRESHOLD_METERS
        if (movementDetected) {
            lastMovementElapsedRealtime = nowElapsedRealtime
        }

        val millisSinceLastMovement = if (lastMovementElapsedRealtime == 0L) {
            Long.MAX_VALUE
        } else {
            (nowElapsedRealtime - lastMovementElapsedRealtime).coerceAtLeast(0L)
        }
        val nextMode = nextTrackingMode(
            current = trackingMode,
            speedMetersPerSecond = speedMetersPerSecond,
            metersSinceLastUpload = metersSinceLastUpload,
            millisSinceLastMovement = millisSinceLastMovement,
        )
        if (nextMode != trackingMode) {
            trackingMode = nextMode
            restartTrackingIfPermitted()
        }

        val repository = locationRepository ?: return
        val person = ownPerson ?: return
        val personLocation = PersonLocation(
            person = person,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            speedMetersPerSecond = speedMetersPerSecond?.toDouble(),
            isMoving = trackingMode == TrackingMode.MOVING,
            updatedAt = Instant.ofEpochMilli(location.time),
        )
        val uploadedPoint = Location(location)

        serviceScope.launch {
            uploadMutex.withLock {
                repository.publishOwnLocation(personLocation)
                    .onSuccess { lastUploadedLocation = uploadedPoint }
                    .onFailure { error -> AndroidCalendarLogger.error(SOURCE_UPLOAD, error) }
            }
        }
    }

    private fun beginTrackingIfPermitted() {
        if (!hasFineLocationPermission()) {
            stopSelf()
            return
        }
        requestLocationUpdates()
    }

    private fun restartTrackingIfPermitted() {
        if (!hasFineLocationPermission()) {
            stopSelf()
            return
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val settings = trackingMode.settings()
        val priority = if (settings.highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, settings.intervalMillis)
            .setMinUpdateDistanceMeters(settings.minUpdateDistanceMeters)
            .setMaxUpdateDelayMillis(settings.maxUpdateDelayMillis)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isTracking = true
        } catch (error: SecurityException) {
            AndroidCalendarLogger.error(SOURCE_PERMISSION, error)
            stopSelf()
        }
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Standortfreigabe",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startLocationForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_selli)
            .setContentTitle("Selli teilt deinen Standort")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "selli_location"
        private const val NOTIFICATION_ID = 2_026_0826
        private const val SOURCE_RUNTIME = "Standortdienst-Abhängigkeiten"
        private const val SOURCE_UPLOAD = "Standortdienst-Upload"
        private const val SOURCE_PERMISSION = "Standortdienst-Berechtigung"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SelliLocationService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SelliLocationService::class.java))
        }
    }
}
