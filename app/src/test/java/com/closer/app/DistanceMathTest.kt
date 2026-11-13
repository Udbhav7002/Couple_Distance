package com.closer.app

import com.closer.app.data.GeoPoint
import com.closer.app.location.DistanceMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DistanceMathTest {

    private fun assertNear(expected: Double, actual: Double, tolerancePct: Double = 0.5) {
        val diff = abs(actual - expected)
        val pct = diff / expected * 100
        assertTrue("Expected ~$expected but got $actual (${String.format("%.1f", pct)}% off)", pct < tolerancePct)
    }

    @Test fun `haversine – same point is zero`() {
        val p = GeoPoint(28.6, 77.2)
        assertEquals(0.0, DistanceMath.haversineM(p, p), 0.001)
    }

    @Test fun `haversine – known distance Mumbai to Delhi ~1150km`() {
        val mumbai = GeoPoint(19.076, 72.877)
        val delhi  = GeoPoint(28.644, 77.216)
        assertNear(1_150_000.0, DistanceMath.haversineM(mumbai, delhi), 1.0)
    }

    @Test fun `haversine – 1 degree latitude ~ 111km`() {
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(1.0, 0.0)
        assertNear(111_195.0, DistanceMath.haversineM(a, b), 0.5)
    }

    @Test fun `haversine – symmetric`() {
        val a = GeoPoint(12.9, 77.6)
        val b = GeoPoint(13.1, 77.5)
        val ab = DistanceMath.haversineM(a, b)
        val ba = DistanceMath.haversineM(b, a)
        assertEquals(ab, ba, 0.001)
    }

    @Test fun `formatDistance – centimeters below 1m`() {
        assertEquals("50 cm", DistanceMath.formatDistance(0.5))
    }

    @Test fun `formatDistance – meters below 1km`() {
        assertEquals("420 m", DistanceMath.formatDistance(420.0))
    }

    @Test fun `formatDistance – km with decimal below 10km`() {
        assertEquals("3.4 km", DistanceMath.formatDistance(3_400.0))
    }

    @Test fun `formatDistance – km rounded above 10km`() {
        assertEquals("26 km", DistanceMath.formatDistance(26_000.0))
    }

    @Test fun `relativeTime – just now`() {
        assertEquals("just now", DistanceMath.relativeTime(System.currentTimeMillis() - 30_000))
    }

    @Test fun `relativeTime – minutes ago`() {
        assertEquals("5m ago", DistanceMath.relativeTime(System.currentTimeMillis() - 5 * 60_000))
    }

    @Test fun `relativeTime – hours ago`() {
        assertEquals("2h ago", DistanceMath.relativeTime(System.currentTimeMillis() - 2 * 3_600_000L))
    }

    @Test fun `relativeTime – days ago`() {
        assertEquals("3d ago", DistanceMath.relativeTime(System.currentTimeMillis() - 3 * 86_400_000L))
    }

    @Test fun `bearingDeg – due North`() {
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(1.0, 0.0)
        assertEquals(0.0f, DistanceMath.bearingDeg(a, b), 0.1f)
    }

    @Test fun `bearingDeg – due East`() {
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(0.0, 1.0)
        assertEquals(90.0f, DistanceMath.bearingDeg(a, b), 0.5f)
    }
}
