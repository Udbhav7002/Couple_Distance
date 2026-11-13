package com.closer.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Single wrapper around FusedLocationProviderClient.
 * - [freshFix] waits up to 8 s for a high-accuracy fix (tap / app open).
 * - [lastKnown] is the instant fallback, displayed honestly with its age.
 * - [startPassive] piggybacks on other apps' GPS usage — effectively free updates.
 */
class LocationEngine(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun lastKnown(): LocationSample? {
        if (!hasPermission()) return null
        return runCatching { client.lastLocation.await()?.toSample() }.getOrNull()
    }

    suspend fun freshFix(
        timeoutMs: Long = 8_000,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY,
    ): LocationSample? {
        if (!hasPermission()) return null
        val request = LocationRequest.Builder(priority, 1_000L)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(true)
            .build()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.toSample()?.let { sample ->
                            if (cont.isActive) cont.resume(sample)
                        }
                    }
                }
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation { client.removeLocationUpdates(callback) }
            }
        }
    }

    /** Passive updates: zero extra power, fires when other apps get fixes. */
    fun startPassive(onSample: (LocationSample) -> Unit): PassiveHandle? {
        if (!hasPermission()) return null
        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 0L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.toSample()?.let(onSample)
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        return PassiveHandle(client, callback)
    }

    class PassiveHandle internal constructor(
        private val client: com.google.android.gms.location.FusedLocationProviderClient,
        private val callback: LocationCallback,
    ) {
        fun stop() = client.removeLocationUpdates(callback)
    }

    private fun Location.toSample() = LocationSample(
        point = GeoPoint(latitude, longitude),
        accuracyM = accuracy,
        timestampMs = time,
    )
}
