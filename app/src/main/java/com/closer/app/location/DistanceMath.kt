package com.closer.app.location

import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceMath {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in meters. */
    fun haversineM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Initial bearing from a to b, in degrees 0..360 (0 = north). */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Float {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /**
     * Median filter over the last few fixes — kills GPS jitter so the displayed
     * number doesn't flicker. Accuracy/timestamp come from the newest sample.
     */
    fun smooth(samples: List<LocationSample>): LocationSample? {
        if (samples.isEmpty()) return null
        val recent = samples.takeLast(3)
        val newest = recent.last()
        val lat = recent.map { it.point.lat }.sorted()[recent.size / 2]
        val lng = recent.map { it.point.lng }.sorted()[recent.size / 2]
        val acc = recent.minOf { it.accuracyM }
        return LocationSample(GeoPoint(lat, lng), acc, newest.timestampMs)
    }

    /**
     * The signature formatting rule:
     *  < 1 m    -> centimeters  ("87 cm")
     *  < 1 km   -> meters       ("420 m")
     *  < 10 km  -> km, 1 decimal ("3.4 km")
     *  >= 10 km -> km           ("26 km")
     */
    fun formatDistance(meters: Double): String = when {
        meters < 1.0 -> "${(meters * 100).roundToInt()} cm"
        meters < 1000.0 -> "${meters.roundToInt()} m"
        meters < 10_000.0 -> String.format("%.1f km", meters / 1000.0)
        else -> "${(meters / 1000.0).roundToInt()} km"
    }

    /** "just now", "2m ago", "3h ago", "5d ago" */
    fun relativeTime(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val s = ((nowMs - thenMs).coerceAtLeast(0)) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60}m ago"
            s < 86_400 -> "${s / 3600}h ago"
            else -> "${s / 86_400}d ago"
        }
    }

    /** True when a new sample is meaningfully different from the last pushed one. */
    fun movedEnough(prev: LocationSample, next: LocationSample, minMeters: Double): Boolean =
        haversineM(prev.point, next.point) >= minMeters
}
