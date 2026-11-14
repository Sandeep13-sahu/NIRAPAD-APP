package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.NirapadRepository
import com.example.data.service.LocationReading
import com.example.data.service.LocationTracker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground location service started immediately upon Deliberate SOS activation.
 * Streams real-time validated GPS fixes to Cloud Firestore (/share_tokens, /sos_sessions,
 * and active /journeys) so trusted contacts see live movement even when the app is backgrounded.
 */
class SosLocationSharingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private val repository by lazy { NirapadRepository(applicationContext) }
    private var sequenceCounter = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCleanly()
            return START_NOT_STICKY
        }

        val sosId = intent?.getStringExtra(EXTRA_SOS_ID).orEmpty()
        val shareTokenId = intent?.getStringExtra(EXTRA_SHARE_TOKEN_ID).orEmpty()
        val journeyId = intent?.getStringExtra(EXTRA_JOURNEY_ID).orEmpty()

        if (sosId.isBlank() || Firebase.auth.currentUser == null || !hasLocationPermission(this)) {
            Log.w(TAG, "Cannot start SOS foreground location service (sosIdBlank=${sosId.isBlank()}, auth=${Firebase.auth.currentUser != null}, perm=${hasLocationPermission(this)})")
            stopCleanly()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()

        val initialNotification = buildNotification(
            contentText = "High-priority SOS active — streaming immediate GPS to trusted contacts...",
            updatesCount = 0,
            isUpdateOnly = false
        )

        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgsType)
        } catch (se: SecurityException) {
            Log.e(TAG, "Foreground service start failed due to missing permission", se)
            stopCleanly()
            return START_NOT_STICKY
        }

        _isRunning.value = true
        _activeSosId.value = sosId
        _broadcastCount.value = 0

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            val tracker = LocationTracker(applicationContext)
            try {
                tracker.observeLocationUpdates(intervalMs = LOCATION_INTERVAL_MS).collect { reading ->
                    if (Firebase.auth.currentUser == null) {
                        stopCleanly()
                        return@collect
                    }
                    // Allow the very first fix even with wider initial accuracy; filter extreme jitter afterward
                    if (_broadcastCount.value > 0 && reading.accuracyMeters > 250.0) {
                        return@collect
                    }

                    sequenceCounter += 1L
                    val stillActive = repository.recordSosLiveLocation(
                        sosId = sosId,
                        shareTokenId = shareTokenId,
                        journeyId = journeyId,
                        lat = reading.lat,
                        lon = reading.lon,
                        accuracyMeters = reading.accuracyMeters,
                        sequence = sequenceCounter
                    )

                    if (!stillActive) {
                        Log.i(TAG, "SOS session $sosId is no longer ACTIVE; stopping background sharing.")
                        stopCleanly()
                        return@collect
                    }

                    val nextCount = _broadcastCount.value + 1
                    _broadcastCount.value = nextCount
                    _lastBroadcastReading.value = reading

                    val statusText = String.format(
                        Locale.US,
                        "Live GPS shared (%d updates): %.4f, %.4f (±%dm)",
                        nextCount,
                        reading.lat,
                        reading.lon,
                        reading.accuracyMeters.toInt()
                    )
                    updateNotification(statusText, nextCount)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "SOS location stream ended: ${e.message}")
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun stopCleanly() {
        trackingJob?.cancel()
        trackingJob = null
        SosSirenPlayer.stopSiren(applicationContext)
        _isRunning.value = false
        _activeSosId.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "High-Priority SOS Alert",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority emergency alert and immediate live GPS sharing to trusted contacts."
                    setShowBadge(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 400L, 200L, 400L, 200L, 600L)
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun updateNotification(contentText: String, updatesCount: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(contentText, updatesCount, isUpdateOnly = true))
    }

    private fun buildNotification(
        contentText: String,
        updatesCount: Int,
        isUpdateOnly: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SosLocationSharingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("HIGH-PRIORITY SOS ACTIVE ($updatesCount GPS shared)")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(isUpdateOnly)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFC62828.toInt())
            .setContentIntent(openPendingIntent)
            .addAction(0, "Stop Sharing", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        _isRunning.value = false
        _activeSosId.value = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SosLocationService"
        private const val CHANNEL_ID = "nirapad_sos_high_priority_channel"
        private const val NOTIFICATION_ID = 9112
        private const val LOCATION_INTERVAL_MS = 3000L

        const val ACTION_START = "com.example.nirapad.action.START_SOS_LOCATION"
        const val ACTION_STOP = "com.example.nirapad.action.STOP_SOS_LOCATION"
        const val EXTRA_SOS_ID = "extra_sos_id"
        const val EXTRA_SHARE_TOKEN_ID = "extra_share_token_id"
        const val EXTRA_JOURNEY_ID = "extra_journey_id"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _activeSosId = MutableStateFlow<String?>(null)
        val activeSosId: StateFlow<String?> = _activeSosId.asStateFlow()

        private val _lastBroadcastReading = MutableStateFlow<LocationReading?>(null)
        val lastBroadcastReading: StateFlow<LocationReading?> = _lastBroadcastReading.asStateFlow()

        private val _broadcastCount = MutableStateFlow(0)
        val broadcastCount: StateFlow<Int> = _broadcastCount.asStateFlow()

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        fun start(context: Context, sosId: String, shareTokenId: String, journeyId: String): Boolean {
            if (sosId.isBlank() || !hasLocationPermission(context)) return false
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, SosLocationSharingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SOS_ID, sosId)
                putExtra(EXTRA_SHARE_TOKEN_ID, shareTokenId)
                putExtra(EXTRA_JOURNEY_ID, journeyId)
            }
            ContextCompat.startForegroundService(appCtx, intent)
            return true
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            if (_isRunning.value) {
                val intent = Intent(appCtx, SosLocationSharingService::class.java).apply {
                    action = ACTION_STOP
                }
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {
                    appCtx.stopService(Intent(appCtx, SosLocationSharingService::class.java))
                }
            } else {
                appCtx.stopService(Intent(appCtx, SosLocationSharingService::class.java))
            }
        }
    }
}
