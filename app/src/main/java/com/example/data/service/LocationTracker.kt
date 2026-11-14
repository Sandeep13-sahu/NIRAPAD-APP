package com.example.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LocationReading(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Double,
    val timestampMillis: Long
)

class LocationTracker(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(intervalMs: Long = 5000L): Flow<LocationReading> = callbackFlow {
        val currentLocCancellation = CancellationTokenSource()

        // Emit last known location immediately if available
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                trySend(
                    LocationReading(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        accuracyMeters = loc.accuracy.toDouble().coerceIn(1.0, 5000.0),
                        timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
            }
        }

        // Also request an immediate fresh high-accuracy fix for instant SOS location sharing
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, currentLocCancellation.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    trySend(
                        LocationReading(
                            lat = loc.latitude,
                            lon = loc.longitude,
                            accuracyMeters = loc.accuracy.toDouble().coerceIn(1.0, 5000.0),
                            timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    )
                }
            }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs.coerceAtMost(2500L))
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LocationReading(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        accuracyMeters = loc.accuracy.toDouble().coerceIn(1.0, 5000.0),
                        timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            currentLocCancellation.cancel()
            client.removeLocationUpdates(callback)
        }
    }
}
