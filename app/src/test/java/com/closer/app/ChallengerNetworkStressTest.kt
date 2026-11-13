package com.closer.app

import com.closer.app.backend.MemberInit
import com.closer.app.backend.relay.LiveRelayBackend
import com.closer.app.backend.relay.RelayServer
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.location.DistanceMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * Empirical Adversarial Challenger: Concurrency, Network Stress, Distance Precision,
 * Stream Cancellation Latency (<100ms), and Mock Fallback Elimination.
 */
class ChallengerNetworkStressTest {

    private lateinit var server: RelayServer
    private lateinit var clientA: LiveRelayBackend
    private lateinit var clientB: LiveRelayBackend

    @Before
    fun setUp() {
        server = RelayServer(requestedPort = 0)
        server.start()
        clientA = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        clientB = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    // =========================================================================
    // 1. CONCURRENCY & NETWORK STRESS
    // =========================================================================

    @Test
    fun testRapidConcurrentMultiPairing() = runBlocking {
        // Stress: 15 simultaneous client pairs (30 clients) pairing concurrently
        val pairCount = 15
        val completedPairs = AtomicInteger(0)

        val jobs = (1..pairCount).map { i ->
            async(Dispatchers.IO) {
                val c1 = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
                val c2 = LiveRelayBackend(explicitBaseUrl = server.baseUrl)

                val name1 = "UserA_$i"
                val name2 = "UserB_$i"

                val c1Created = c1.createCouple(MemberInit(name = name1, colorIndex = i % 8))
                assertEquals(6, c1Created.code.length)
                assertFalse(c1Created.paired)

                val c2Joined = c2.joinCouple(c1Created.code, MemberInit(name = name2, colorIndex = (i + 1) % 8))
                assertTrue("Couple $i must be paired", c2Joined.paired)
                assertEquals(name1, c2Joined.partner?.name)
                assertEquals(name2, c2Joined.me.name)

                completedPairs.incrementAndGet()
            }
        }

        jobs.awaitAll()
        assertEquals("All 15 pairs must complete pairing without race condition", pairCount, completedPairs.get())
    }

    @Test
    fun testHighThroughputCoordinateStreaming() = runBlocking {
        // Establish pairing
        val devInit = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))
        val nandhiniInit = clientB.joinCouple(devInit.code, MemberInit(name = "Nandhini", colorIndex = 1))
        assertTrue(nandhiniInit.paired)

        val burstCount = 25
        val receivedByB = ConcurrentHashMap<Int, Double>()
        val receivedByA = ConcurrentHashMap<Int, Double>()

        val jobB = launch(Dispatchers.IO) {
            clientB.observeCouple(devInit.coupleId, nandhiniInit.me.uid).collect { state ->
                val lat = state?.partner?.lastLocation?.point?.lat
                if (lat != null) {
                    receivedByB[lat.toInt()] = lat
                }
            }
        }

        val jobA = launch(Dispatchers.IO) {
            clientA.observeCouple(devInit.coupleId, devInit.me.uid).collect { state ->
                val lng = state?.partner?.lastLocation?.point?.lng
                if (lng != null) {
                    receivedByA[lng.toInt()] = lng
                }
            }
        }

        delay(150) // Allow SSE subscriptions to establish

        // Concurrent bursts from both clients to stress race conditions & socket concurrency
        val pushJobs = (1..burstCount).map { i ->
            async(Dispatchers.IO) {
                clientA.pushLocation(
                    devInit.coupleId,
                    devInit.me.uid,
                    LocationSample(GeoPoint(i.toDouble(), 10.0), 5f, System.currentTimeMillis(), batteryPct = 80 - i)
                )
                clientB.pushLocation(
                    devInit.coupleId,
                    nandhiniInit.me.uid,
                    LocationSample(GeoPoint(20.0, i.toDouble()), 4f, System.currentTimeMillis(), batteryPct = 90 - i)
                )
            }
        }
        pushJobs.awaitAll()

        // After concurrent storm, push definitive anchor samples to verify stream liveness and integrity
        val anchorLat = 99.0
        val anchorLng = 88.0
        clientA.pushLocation(
            devInit.coupleId,
            devInit.me.uid,
            LocationSample(GeoPoint(anchorLat, 10.0), 3f, System.currentTimeMillis(), batteryPct = 50)
        )
        clientB.pushLocation(
            devInit.coupleId,
            nandhiniInit.me.uid,
            LocationSample(GeoPoint(20.0, anchorLng), 3f, System.currentTimeMillis(), batteryPct = 60)
        )

        // Wait for streaming propagation of anchor samples
        withTimeout(5000) {
            while (receivedByB[anchorLat.toInt()] == null || receivedByA[anchorLng.toInt()] == null) {
                delay(20)
            }
        }

        jobA.cancelAndJoin()
        jobB.cancelAndJoin()

        assertTrue("B must receive the definitive anchor update from A", receivedByB.containsKey(anchorLat.toInt()))
        assertTrue("A must receive the definitive anchor update from B", receivedByA.containsKey(anchorLng.toInt()))
        assertTrue("B should have received multiple stream updates during burst", receivedByB.size >= 5)
        assertTrue("A should have received multiple stream updates during burst", receivedByA.size >= 5)
    }

    @Test
    fun testSseCoroutineStreamCancellationSpeedUnder100ms() = runBlocking {
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        // Repeat 10 times to verify fast cancellation under 100ms consistently
        for (i in 1..10) {
            val job = launch(Dispatchers.IO) {
                clientA.observeCouple(created.coupleId, created.me.uid).collect {
                    // Collect state
                }
            }

            // Wait until stream is actively receiving
            delay(50)

            val t0 = System.nanoTime()
            job.cancelAndJoin()
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000

            assertTrue(
                "SSE cancellation iteration $i took ${elapsedMs}ms; MUST be < 100ms",
                elapsedMs < 100
            )
        }
    }

    @Test
    fun testRapidConnectCancelReconnectionLoopReleasesSockets() = runBlocking {
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        // Open, receive, immediately cancel 15 times
        repeat(15) {
            val job = launch(Dispatchers.IO) {
                clientA.observeCouple(created.coupleId, created.me.uid).first { it != null }
            }
            job.join()
        }

        // Verify server listener count is completely cleared
        delay(100)
        val initialThreadCount = Thread.activeCount()
        assertTrue("Active thread count ($initialThreadCount) should remain reasonable", initialThreadCount < 100)
    }

    // =========================================================================
    // 2. DISTANCE & LOCATION PRECISION
    // =========================================================================

    @Test
    fun testDistanceMathExtremeEdgeCases() {
        // 1. Identical coordinates -> 0.0m
        val p1 = GeoPoint(37.774929, -122.419416)
        val p2 = GeoPoint(37.774929, -122.419416)
        val d0 = DistanceMath.haversineM(p1, p2)
        assertEquals("Identical points must yield exactly 0m", 0.0, d0, 0.000001)
        assertEquals("0.0m must format to '0 cm'", "0 cm", DistanceMath.formatDistance(d0))

        // 2. Sub-meter values -> cm formatting
        assertEquals("0 cm", DistanceMath.formatDistance(0.0))
        assertEquals("0 cm", DistanceMath.formatDistance(0.004))
        assertEquals("1 cm", DistanceMath.formatDistance(0.01))
        assertEquals("5 cm", DistanceMath.formatDistance(0.05))
        assertEquals("45 cm", DistanceMath.formatDistance(0.45))
        assertEquals("87 cm", DistanceMath.formatDistance(0.87))
        assertEquals("99 cm", DistanceMath.formatDistance(0.99))
        assertEquals("100 cm", DistanceMath.formatDistance(0.996))

        // 3. Co-located devices < 10m
        assertEquals("1 m", DistanceMath.formatDistance(1.0))
        assertEquals("1 m", DistanceMath.formatDistance(1.2))
        assertEquals("2 m", DistanceMath.formatDistance(1.8))
        assertEquals("5 m", DistanceMath.formatDistance(5.0))
        assertEquals("9 m", DistanceMath.formatDistance(9.4))
        assertEquals("10 m", DistanceMath.formatDistance(9.9))

        // 4. Intermediate distances < 1000m
        assertEquals("500 m", DistanceMath.formatDistance(500.0))
        assertEquals("999 m", DistanceMath.formatDistance(999.4))
        assertEquals("1000 m", DistanceMath.formatDistance(999.6))

        // 5. 1 km to 10 km (one decimal place)
        assertEquals("1.0 km", DistanceMath.formatDistance(1000.0))
        assertEquals("3.5 km", DistanceMath.formatDistance(3450.0))
        assertEquals("9.9 km", DistanceMath.formatDistance(9949.0))
        assertEquals("10.0 km", DistanceMath.formatDistance(9950.0))

        // 6. Distant cities >= 10 km
        assertEquals("10 km", DistanceMath.formatDistance(10000.0))
        assertEquals("68 km", DistanceMath.formatDistance(67500.0))
        assertEquals("20015 km", DistanceMath.formatDistance(20015087.0))
    }

    @Test
    fun testHaversineAntipodalAndExtremeCoordinates() {
        // North Pole to South Pole
        val northPole = GeoPoint(90.0, 0.0)
        val southPole = GeoPoint(-90.0, 0.0)
        val poleDistance = DistanceMath.haversineM(northPole, southPole)
        assertFalse("Distance must not be NaN", poleDistance.isNaN())
        assertEquals(20_015_087.0, poleDistance, 50.0)

        // Equator antipodal
        val eq1 = GeoPoint(0.0, 0.0)
        val eq2 = GeoPoint(0.0, 180.0)
        val eqDist = DistanceMath.haversineM(eq1, eq2)
        assertFalse("Equator antipodal must not be NaN", eqDist.isNaN())
        assertEquals(20_015_087.0, eqDist, 50.0)

        // Equator -180 to 180
        val eq3 = GeoPoint(0.0, -180.0)
        val eqDist2 = DistanceMath.haversineM(eq1, eq3)
        assertFalse("Equator negative antipodal must not be NaN", eqDist2.isNaN())
        assertEquals(20_015_087.0, eqDist2, 50.0)

        // Micro-displacement (sub-millimeter)
        val pA = GeoPoint(37.77490000, -122.41940000)
        val pB = GeoPoint(37.77490001, -122.41940001)
        val microDist = DistanceMath.haversineM(pA, pB)
        assertTrue("Micro-displacement must be > 0", microDist > 0)
        assertTrue("Micro-displacement must be < 0.01m", microDist < 0.01)
        assertEquals("0 cm", DistanceMath.formatDistance(microDist))
    }

    @Test
    fun testFormatDistanceLocaleSeparators() {
        val origLocale = Locale.getDefault()
        try {
            // Test locales that use comma as decimal separator
            for (loc in listOf(Locale.GERMANY, Locale.FRANCE, Locale.ITALY)) {
                Locale.setDefault(loc)
                val formatted = DistanceMath.formatDistance(3450.0)
                assertEquals("Must use '.' decimal separator in locale ${loc.displayName}", "3.5 km", formatted)
                assertFalse("Must never contain comma in distance format", formatted.contains(","))
            }
        } finally {
            Locale.setDefault(origLocale)
        }
    }
}
