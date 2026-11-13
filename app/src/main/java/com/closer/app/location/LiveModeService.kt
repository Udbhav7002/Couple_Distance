package com.closer.app.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.di.Locator
import com.closer.app.notify.Notifier
import com.closer.app.refresh.Refresher
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Live mode: high-accuracy foreground service that keeps your side fresh while you
 * move (≥0.5 m or 3 s), so your partner's widget and app are always current in real-time.
 */
class LiveModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private var lastPushedAt = 0L
    private var lastPushed: LocationSample? = null
    private var activeCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val container = Locator.init(this)
        startForeground(Notifier.ID_LIVE, container.notifier.live(partnerInitialForNotification()))
        startTracking()
        return START_STICKY
    }

    private fun partnerInitialForNotification(): String =
        Locator.container.snapshots.load().partnerInitial.ifBlank { "your partner" }

    private fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val sample = LocationSample(
                    point = GeoPoint(loc.latitude, loc.longitude),
                    accuracyM = loc.accuracy,
                    timestampMs = loc.time,
                )
                val now = System.currentTimeMillis()
                val moved = lastPushed == null ||
                    DistanceMath.movedEnough(lastPushed!!, sample, MIN_MOVE_M)
                if (moved || now - lastPushedAt > MIN_INTERVAL_MS) {
                    lastPushed = sample
                    lastPushedAt = now
                    scope.launch { Refresher.sync(this@LiveModeService, sample = sample, isLive = true) }
                }
            }
        }
        activeCallback = callback
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        activeCallback?.let { runCatching { client.removeLocationUpdates(it) } }
        activeCallback = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.closer.app.LIVE_STOP"
        private const val MIN_MOVE_M = 0.5
        private const val MIN_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LiveModeService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LiveModeService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
