package com.closer.app

import com.closer.app.backend.MemberInit
import com.closer.app.backend.relay.LiveRelayBackend
import com.closer.app.backend.relay.RelayServer
import com.closer.app.data.ConnectionStatus
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.location.DistanceMath
import com.closer.app.widget.WidgetMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Milestone 5: Comprehensive Dual-Client Integration Test.
 *
 * Verifies two independent client instances ("Dev" and "Nandhini") interacting
 * with LiveRelayBackend and RelayServer in real-time:
 *
 * a) Real 6-Character Pairing Handshake:
 *    - Client A ("Dev", colorIndex 0) creates couple and receives a 6-character code.
 *    - Client B ("Nandhini", colorIndex 1) joins with that code.
 *    - Both clients observe shared CoupleState (paired == true, matching coupleId, correct reciprocal partner names).
 *    - Zero mock fallback verification.
 *
 * b) Physical Proximity & Co-Location Distance:
 *    - Co-located coordinates (~1.8m apart): verify distance < 10m and formatDistance == "2 m".
 *    - Centimeter coordinates (~0.45m apart): verify distance < 1.0m and formatDistance == "45 cm".
 *    - Distant separation (SF to San Jose ~67km): verify distance ~67km and formats cleanly in km.
 *
 * c) Bidirectional Real-Time Live Streaming:
 *    - Burst of continuous GPS location samples streamed bidirectionally.
 *    - SSE coroutine stream updates coordinates live and updates battery levels.
 *    - SSE stream handles coroutine cancellation cleanly without socket deadlocks.
 *
 * d) Sharing & Privacy Toggles:
 *    - Client A disables location sharing (setSharing(false)).
 *    - Client B receives state with paused == true.
 *    - WidgetSnapshot resolves status to ConnectionStatus.Paused ("Paused").
 *
 * e) Production Integrity:
 *    - Programmatically asserts that no source or test files reference MockBackend,
 *      seedMockIfNeeded, or hardcoded simulated partner "Ava".
 */
class DualClientIntegrationTest {

    private lateinit var server: RelayServer
    private lateinit var clientA: LiveRelayBackend // "Dev", colorIndex 0
    private lateinit var clientB: LiveRelayBackend // "Nandhini", colorIndex 1

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
    // a) Real 6-Character Pairing Handshake
    // =========================================================================

    @Test
    fun testRealSixCharacterPairingHandshake() = runBlocking {
        // Client A: Dev, colorIndex 0 creates couple
        val devUid = clientA.ensureAuth()
        assertTrue("Dev UID should be authenticated and non-empty", devUid.isNotEmpty())

        val devCreated = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))
        assertEquals("Pairing code must be exactly 6 characters", 6, devCreated.code.length)
        assertFalse("Created couple must initially be unpaired", devCreated.paired)
        assertEquals("Dev", devCreated.me.name)
        assertEquals(0, devCreated.me.colorIndex)
        assertEquals(devUid, devCreated.me.uid)
        assertNull("Partner must initially be null", devCreated.partner)

        // Asynchronous listener on Client A to verify live pairing notification
        val devObservedDeferred = async(Dispatchers.IO) {
            withTimeout(5_000) {
                clientA.observeCouple(devCreated.coupleId, devUid)
                    .filterNotNull()
                    .first { it.paired && it.partner != null }
            }
        }

        // Client B: Nandhini, colorIndex 1 joins using the 6-character code
        val nandhiniUid = clientB.ensureAuth()
        assertTrue("Nandhini UID should be authenticated and non-empty", nandhiniUid.isNotEmpty())
        assertNotEquals("Dev and Nandhini must have unique UIDs", devUid, nandhiniUid)

        val nandhiniJoined = clientB.joinCouple(
            devCreated.code,
            MemberInit(name = "Nandhini", colorIndex = 1)
        )

        // Validate Client B's observed state immediately after joining
        assertEquals(devCreated.coupleId, nandhiniJoined.coupleId)
        assertTrue("Client B must observe paired == true", nandhiniJoined.paired)
        assertEquals("Nandhini", nandhiniJoined.me.name)
        assertEquals(1, nandhiniJoined.me.colorIndex)
        assertEquals(nandhiniUid, nandhiniJoined.me.uid)
        assertNotNull("Client B must see partner", nandhiniJoined.partner)
        assertEquals("Dev", nandhiniJoined.partner?.name)
        assertEquals(0, nandhiniJoined.partner?.colorIndex)
        assertEquals(devUid, nandhiniJoined.partner?.uid)

        // Validate Client A's observed state received via real-time SSE stream
        val devObserved = devObservedDeferred.await()
        assertEquals(devCreated.coupleId, devObserved.coupleId)
        assertTrue("Client A must observe paired == true", devObserved.paired)
        assertEquals("Dev", devObserved.me.name)
        assertEquals(devUid, devObserved.me.uid)
        assertNotNull("Client A must see partner", devObserved.partner)
        assertEquals("Nandhini", devObserved.partner?.name)
        assertEquals(1, devObserved.partner?.colorIndex)
        assertEquals(nandhiniUid, devObserved.partner?.uid)

        // Zero mock fallback validation
        assertNotEquals("Ava", devObserved.partner?.name)
        assertNotEquals("Ava", nandhiniJoined.partner?.name)
    }

    // =========================================================================
    // b) Physical Proximity & Co-Location Distance (<10m, cm-level, distant)
    // =========================================================================

    @Test
    fun testPhysicalProximityColocatedSub10Meters() = runBlocking {
        val devUid = clientA.ensureAuth()
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = clientB.ensureAuth()
        clientB.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 1))

        // Client A at (37.774929, -122.419416)
        val devPoint = GeoPoint(37.774929, -122.419416)
        clientA.pushLocation(
            created.coupleId,
            devUid,
            LocationSample(point = devPoint, accuracyM = 2.0f, timestampMs = System.currentTimeMillis())
        )

        // Client B at (37.774945, -122.419416) (~1.8m apart)
        val nandhiniPoint = GeoPoint(37.774945, -122.419416)
        clientB.pushLocation(
            created.coupleId,
            nandhiniUid,
            LocationSample(point = nandhiniPoint, accuracyM = 2.0f, timestampMs = System.currentTimeMillis())
        )

        // Client A observes Client B's location
        val stateObservedByDev = withTimeout(5_000) {
            clientA.observeCouple(created.coupleId, devUid)
                .filterNotNull()
                .first { it.partner?.lastLocation != null }
        }

        val partnerLoc = stateObservedByDev.partner!!.lastLocation!!.point
        assertEquals(nandhiniPoint.lat, partnerLoc.lat, 0.000001)
        assertEquals(nandhiniPoint.lng, partnerLoc.lng, 0.000001)

        val distance = DistanceMath.haversineM(devPoint, partnerLoc)
        assertTrue("Co-located distance must be < 10 meters (was $distance m)", distance < 10.0)
        assertTrue("Distance should be approximately 1.8m (was $distance m)", abs(distance - 1.78) < 0.2)

        val formatted = DistanceMath.formatDistance(distance)
        assertEquals("2 m", formatted)
    }

    @Test
    fun testPhysicalProximityCentimeterLevel() = runBlocking {
        val devUid = clientA.ensureAuth()
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = clientB.ensureAuth()
        clientB.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 1))

        // Client A at (37.774929, -122.419416)
        val devPoint = GeoPoint(37.774929, -122.419416)
        clientA.pushLocation(
            created.coupleId,
            devUid,
            LocationSample(point = devPoint, accuracyM = 1.0f, timestampMs = System.currentTimeMillis())
        )

        // Client B at coordinates ~0.45m apart
        // 0.45m / 6371000m * 180 / PI = 0.0000040465 degrees
        val nandhiniPoint = GeoPoint(37.77493305, -122.419416)
        clientB.pushLocation(
            created.coupleId,
            nandhiniUid,
            LocationSample(point = nandhiniPoint, accuracyM = 1.0f, timestampMs = System.currentTimeMillis())
        )

        val stateObservedByDev = withTimeout(5_000) {
            clientA.observeCouple(created.coupleId, devUid)
                .filterNotNull()
                .first { it.partner?.lastLocation != null }
        }

        val partnerLoc = stateObservedByDev.partner!!.lastLocation!!.point
        val distance = DistanceMath.haversineM(devPoint, partnerLoc)

        assertTrue("Centimeter distance must be < 1.0 meter (was $distance m)", distance < 1.0)
        assertTrue("Distance must be greater than 0.4m (was $distance m)", distance > 0.4)

        val formatted = DistanceMath.formatDistance(distance)
        assertEquals("45 cm", formatted)
    }

    @Test
    fun testPhysicalProximityDistantSeparationSanFranciscoToSanJose() = runBlocking {
        val devUid = clientA.ensureAuth()
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = clientB.ensureAuth()
        clientB.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 1))

        // Client A at SF (37.7749, -122.4194)
        val sfPoint = GeoPoint(37.7749, -122.4194)
        clientA.pushLocation(
            created.coupleId,
            devUid,
            LocationSample(point = sfPoint, accuracyM = 5.0f, timestampMs = System.currentTimeMillis())
        )

        // Client B at San Jose (37.3382, -121.8863) (~67km apart)
        val sjPoint = GeoPoint(37.3382, -121.8863)
        clientB.pushLocation(
            created.coupleId,
            nandhiniUid,
            LocationSample(point = sjPoint, accuracyM = 5.0f, timestampMs = System.currentTimeMillis())
        )

        val stateObservedByDev = withTimeout(5_000) {
            clientA.observeCouple(created.coupleId, devUid)
                .filterNotNull()
                .first { it.partner?.lastLocation != null }
        }

        val partnerLoc = stateObservedByDev.partner!!.lastLocation!!.point
        val distanceM = DistanceMath.haversineM(sfPoint, partnerLoc)

        // SF to San Jose is ~67.5 km (67,000 - 68,000 m)
        assertTrue("Distance should be approximately 67km (was ${distanceM / 1000.0} km)", distanceM in 66_000.0..69_000.0)

        val formatted = DistanceMath.formatDistance(distanceM)
        assertTrue("Formatted distance must end with ' km' (was '$formatted')", formatted.endsWith(" km"))
        assertEquals("68 km", formatted)
    }

    // =========================================================================
    // c) Bidirectional Real-Time Live Streaming & Battery Sync
    // =========================================================================

    @Test
    fun testBidirectionalRealtimeLiveStreamingAndBatteryLevels() = runBlocking {
        val devUid = clientA.ensureAuth()
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = clientB.ensureAuth()
        clientB.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 1))

        // Stream a burst of continuous GPS location samples across clients
        val devSamples = listOf(
            LocationSample(GeoPoint(37.774900, -122.419400), 3.0f, 1000L, batteryPct = 95),
            LocationSample(GeoPoint(37.774910, -122.419400), 3.0f, 2000L, batteryPct = 94),
            LocationSample(GeoPoint(37.774920, -122.419400), 3.0f, 3000L, batteryPct = 93),
            LocationSample(GeoPoint(37.774930, -122.419400), 3.0f, 4000L, batteryPct = 92),
            LocationSample(GeoPoint(37.774940, -122.419400), 3.0f, 5000L, batteryPct = 90),
        )

        val nandhiniSamples = listOf(
            LocationSample(GeoPoint(37.774900, -122.419400), 2.5f, 1050L, batteryPct = 80),
            LocationSample(GeoPoint(37.774905, -122.419400), 2.5f, 2050L, batteryPct = 79),
            LocationSample(GeoPoint(37.774915, -122.419400), 2.5f, 3050L, batteryPct = 78),
            LocationSample(GeoPoint(37.774925, -122.419400), 2.5f, 4050L, batteryPct = 77),
            LocationSample(GeoPoint(37.774935, -122.419400), 2.5f, 5050L, batteryPct = 75),
        )

        // Client B observes Dev's incoming updates live
        val bCollector = async(Dispatchers.IO) {
            val received = mutableListOf<LocationSample>()
            val receivedBatteries = mutableListOf<Int>()
            withTimeout(10_000) {
                clientB.observeCouple(created.coupleId, nandhiniUid)
                    .filterNotNull()
                    .collect { state ->
                        state.partner?.lastLocation?.let { loc ->
                            if (received.isEmpty() || received.last().timestampMs != loc.timestampMs) {
                                received.add(loc)
                                state.partner?.battery?.let { receivedBatteries.add(it) }
                            }
                        }
                        if (received.size >= devSamples.size) return@collect
                    }
            }
            received to receivedBatteries
        }

        // Client A observes Nandhini's incoming updates live
        val aCollector = async(Dispatchers.IO) {
            val received = mutableListOf<LocationSample>()
            val receivedBatteries = mutableListOf<Int>()
            withTimeout(10_000) {
                clientA.observeCouple(created.coupleId, devUid)
                    .filterNotNull()
                    .collect { state ->
                        state.partner?.lastLocation?.let { loc ->
                            if (received.isEmpty() || received.last().timestampMs != loc.timestampMs) {
                                received.add(loc)
                                state.partner?.battery?.let { receivedBatteries.add(it) }
                            }
                        }
                        if (received.size >= nandhiniSamples.size) return@collect
                    }
            }
            received to receivedBatteries
        }

        // Push continuous burst interleaved across clients
        for (i in 0 until 5) {
            clientA.pushLocation(created.coupleId, devUid, devSamples[i])
            clientB.pushLocation(created.coupleId, nandhiniUid, nandhiniSamples[i])
            delay(50)
        }

        val (devReceivedByB, devBatteriesReceivedByB) = bCollector.await()
        val (nandhiniReceivedByA, nandhiniBatteriesReceivedByA) = aCollector.await()

        assertEquals("Client B should receive all 5 samples from Dev", 5, devReceivedByB.size)
        assertEquals("Client A should receive all 5 samples from Nandhini", 5, nandhiniReceivedByA.size)

        // Verify coordinate progression
        for (i in 0 until 5) {
            assertEquals(devSamples[i].point.lat, devReceivedByB[i].point.lat, 0.000001)
            assertEquals(nandhiniSamples[i].point.lat, nandhiniReceivedByA[i].point.lat, 0.000001)
        }

        // Verify live battery updates
        assertEquals(90, devBatteriesReceivedByB.last())
        assertEquals(75, nandhiniBatteriesReceivedByA.last())
    }

    @Test
    fun testLiveStreamingCancellationCleanWithoutDeadlock() = runBlocking {
        val devUid = clientA.ensureAuth()
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = clientB.ensureAuth()
        clientB.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 1))

        // Start SSE stream collection in a background coroutine
        val job = launch(Dispatchers.IO) {
            clientA.observeCouple(created.coupleId, devUid).collect {
                // Keep streaming
            }
        }

        // Allow connection to establish
        delay(200)
        assertTrue("Streaming job should be active", job.isActive)

        // Cancel job and verify it terminates quickly without socket deadlock
        val cancelStartTime = System.currentTimeMillis()
        withTimeout(2_000) {
            job.cancelAndJoin()
        }
        val cancelDuration = System.currentTimeMillis() - cancelStartTime

        assertFalse("Streaming job must be cancelled", job.isActive)
        assertTrue("Cancellation should complete cleanly within 2 seconds (took ${cancelDuration}ms)", cancelDuration < 2000)
    }

    // =========================================================================
    // d) Sharing & Privacy Toggles
    // =========================================================================

    @Test
    fun testSharingAndPrivacyTogglesWidgetSnapshotResolution() = runBlocking {
        val devUid = clientA.ensureAuth()
        val created = clientA.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = clientB.ensureAuth()
        clientB.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 1))

        // Initial state: location sharing enabled by default
        val initialStateDev = withTimeout(5_000) {
            clientB.observeCouple(created.coupleId, nandhiniUid).filterNotNull().first()
        }
        assertTrue("Dev sharing should initially be enabled", initialStateDev.partner?.sharingOn == true)
        val initialSnap = WidgetMapper.fromState(initialStateDev)
        assertFalse("Initial snapshot should not be paused", initialSnap.paused)

        // Client A disables location sharing: setSharing(enabled = false)
        clientA.setSharing(created.coupleId, devUid, on = false)

        // Client B receives the update with paused == true
        val stateAfterPause = withTimeout(5_000) {
            clientB.observeCouple(created.coupleId, nandhiniUid)
                .filterNotNull()
                .first { it.partner?.sharingOn == false }
        }
        assertNotNull(stateAfterPause.partner)
        assertFalse("Partner sharingOn must be false", stateAfterPause.partner!!.sharingOn)

        // Map to WidgetSnapshot from Client B's perspective
        val snapAfterPause = WidgetMapper.fromState(stateAfterPause)
        assertTrue("WidgetSnapshot paused must be true when partner sharing is disabled", snapAfterPause.paused)

        // Verify WidgetSnapshot resolves status to "Paused"
        assertEquals("Paused", snapAfterPause.resolveStatus())
        assertEquals("Paused", snapAfterPause.statusText)
        assertEquals(ConnectionStatus.Paused, snapAfterPause.connectionStatus())
        assertEquals(ConnectionStatus.Paused, ConnectionStatus.resolve(snapAfterPause))

        // Client A re-enables location sharing: setSharing(enabled = true)
        clientA.setSharing(created.coupleId, devUid, on = true)

        val stateAfterResume = withTimeout(5_000) {
            clientB.observeCouple(created.coupleId, nandhiniUid)
                .filterNotNull()
                .first { it.partner?.sharingOn == true }
        }
        assertTrue("Partner sharingOn must be true after resumption", stateAfterResume.partner!!.sharingOn)

        val snapAfterResume = WidgetMapper.fromState(stateAfterResume)
        assertFalse("WidgetSnapshot paused must be false after resumption", snapAfterResume.paused)
        assertNotEquals(ConnectionStatus.Paused, snapAfterResume.connectionStatus())
    }

    // =========================================================================
    // e) Verify Production Integrity
    // =========================================================================

    @Test
    fun testProductionIntegrityNoMockFallbackOrAvaReferences() {
        val cwd = File(".").canonicalFile
        val srcDir = when {
            File(cwd, "src").isDirectory -> File(cwd, "src")
            File(cwd, "app/src").isDirectory -> File(cwd, "app/src")
            File(cwd.parentFile, "app/src").isDirectory -> File(cwd.parentFile, "app/src")
            else -> File(".").walk().firstOrNull { it.isDirectory && it.name == "src" && File(it, "main").exists() }
        }
        assertNotNull("src directory must exist (checked cwd=$cwd)", srcDir)
        assertTrue("src directory must be a directory", srcDir!!.isDirectory)

        val mainDir = File(srcDir, "main")
        val testDir = File(srcDir, "test")
        assertTrue("src/main must exist", mainDir.exists())
        assertTrue("src/test must exist", testDir.exists())

        // Build forbidden tokens dynamically to avoid literal self-matches in this file
        val mockBackendToken = "Mock" + "Backend"
        val seedMockToken = "seedMock" + "IfNeeded"
        val avaExactWordRegex = Regex("""\bA""" + """va\b""")

        val scannedFiles = mutableListOf<File>()
        val violations = mutableListOf<String>()

        fun scan(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { scan(it) }
            } else if (file.isFile && (file.extension in listOf("kt", "java", "xml", "json"))) {
                // Exclude this test file itself from the check
                if (file.name == "DualClientIntegrationTest.kt") return

                scannedFiles.add(file)
                val content = file.readText()

                if (content.contains(mockBackendToken)) {
                    violations.add("${file.path}: contains forbidden token '$mockBackendToken'")
                }
                if (content.contains(seedMockToken)) {
                    violations.add("${file.path}: contains forbidden token '$seedMockToken'")
                }
                if (avaExactWordRegex.containsMatchIn(content)) {
                    violations.add("${file.path}: contains forbidden simulated partner 'Ava'")
                }
            }
        }

        scan(mainDir)
        scan(testDir)

        assertTrue(
            "Must have scanned at least 20 source/test files (scanned ${scannedFiles.size})",
            scannedFiles.size >= 20
        )

        assertTrue(
            "Production integrity violations found:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
