package com.example.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs

data class LocationReading(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Double,
    val timestampMillis: Long,
    val bearingDegrees: Float? = null,
    val speedMps: Float? = null
)

class LocationTracker(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(intervalMs: Long = 1500L): Flow<LocationReading> = callbackFlow {
        val currentLocCancellation = CancellationTokenSource()

        fun android.location.Location.toReading(): LocationReading = LocationReading(
            lat = latitude,
            lon = longitude,
            accuracyMeters = accuracy.toDouble().coerceIn(1.0, 5000.0),
            timestampMillis = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            bearingDegrees = if (hasBearing()) bearing else null,
            speedMps = if (hasSpeed()) speed else null
        )

        try {
            // Emit last known location immediately if available
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    trySend(loc.toReading())
                }
            }

            // Request an immediate fresh high-accuracy fix for instant map & SOS lock
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, currentLocCancellation.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        trySend(loc.toReading())
                    }
                }
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(500L))
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(loc.toReading())
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            currentLocCancellation.cancel()
            close()
            return@callbackFlow
        }

        awaitClose {
            currentLocCancellation.cancel()
            client.removeLocationUpdates(callback)
        }
    }
}

class DeviceCompassTracker(private val context: Context) {
    fun observeCompassAzimuth(): Flow<Float> = callbackFlow {
        val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            close()
            return@callbackFlow
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelSensor = if (rotationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val magnetSensor = if (rotationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

        if (rotationSensor == null && (accelSensor == null || magnetSensor == null)) {
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var lastGravity: FloatArray? = null
        var lastGeomagnetic: FloatArray? = null
        var lastSentAzimuth: Float? = null
        var lastSentMs = 0L

        fun emitAzimuth(azimuthRad: Float) {
            val deg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toFloat()
            val now = System.currentTimeMillis()
            val prev = lastSentAzimuth
            val diff = if (prev == null) 360f else abs(((deg - prev + 540f) % 360f) - 180f)
            if (prev == null || (diff >= 2.0f && now - lastSentMs >= 65L)) {
                lastSentAzimuth = deg
                lastSentMs = now
                trySend(deg)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR,
                    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        emitAzimuth(orientationAngles[0])
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lastGravity = event.values.clone()
                        val g = lastGravity ?: return
                        val m = lastGeomagnetic ?: return
                        if (SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) {
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            emitAzimuth(orientationAngles[0])
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lastGeomagnetic = event.values.clone()
                        val g = lastGravity ?: return
                        val m = lastGeomagnetic ?: return
                        if (SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) {
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            emitAzimuth(orientationAngles[0])
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
