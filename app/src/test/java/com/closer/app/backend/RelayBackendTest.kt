package com.closer.app.backend

import com.closer.app.backend.relay.CodeGenerator
import com.closer.app.backend.relay.LiveRelayBackend
import com.closer.app.backend.relay.RelayServer
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.location.DistanceMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RelayBackendTest {

    private lateinit var server: RelayServer
    private lateinit var devBackend: LiveRelayBackend
    private lateinit var nandhiniBackend: LiveRelayBackend

    @Before
    fun setUp() {
        // Start RelayServer on an ephemeral OS port
        server = RelayServer(requestedPort = 0)
        server.start()

        devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        nandhiniBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun testCodeGeneratorProperties() {
        val codes = (1..100).map { CodeGenerator.generate() }
        for (code in codes) {
            assertEquals(6, code.length)
            for (ch in code) {
                assertTrue("Character $ch should be in alphabet", CodeGenerator.ALPHABET.contains(ch))
            }
        }
        val unique = codes.toSet()
        assertTrue("Generated codes should be mostly unique", unique.size >= 95)
    }

    @Test
    fun testPairingBetweenDevAndNandhini() = runBlocking {
        // Dev ensures auth and creates a couple
        val devUid = devBackend.ensureAuth()
        assertTrue(devUid.isNotEmpty())

        val created = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))
        assertEquals(6, created.code.length)
        assertFalse(created.paired)
        assertEquals("Dev", created.me.name)
        assertEquals(devUid, created.me.uid)
        assertNull(created.partner)

        // Nandhini ensures auth and joins with Dev's code
        val nandhiniUid = nandhiniBackend.ensureAuth()
        assertTrue(nandhiniUid.isNotEmpty())

        val joined = nandhiniBackend.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 5))
        assertEquals(created.coupleId, joined.coupleId)
        assertTrue(joined.paired)
        assertEquals("Nandhini", joined.me.name)
        assertEquals(nandhiniUid, joined.me.uid)
        assertNotNull(joined.partner)
        assertEquals("Dev", joined.partner?.name)
        assertEquals(devUid, joined.partner?.uid)
    }

    @Test
    fun testRealtimeStreamingStateObservation() = runBlocking {
        val devUid = devBackend.ensureAuth()
        val created = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        // Start listening to Dev's stream asynchronously
        val devCollector = async(Dispatchers.IO) {
            withTimeout(10_000) {
                devBackend.observeCouple(created.coupleId, devUid)
                    .first { it?.partner != null }
            }
        }

        // Nandhini joins
        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 5))

        val latestDevState = devCollector.await()
        assertNotNull(latestDevState)
        assertTrue(latestDevState!!.paired)
        assertEquals("Nandhini", latestDevState.partner?.name)
        assertEquals(nandhiniUid, latestDevState.partner?.uid)
    }

    @Test
    fun testLiveLocationSyncAndSub10mProximity() = runBlocking {
        val devUid = devBackend.ensureAuth()
        val created = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 5))

        // Dev coordinates
        val devPoint = GeoPoint(12.971600, 77.594600)
        val devSample = LocationSample(point = devPoint, accuracyM = 3.5f, timestampMs = System.currentTimeMillis())
        devBackend.pushLocation(created.coupleId, devUid, devSample)

        // Nandhini coordinates: co-located ~7.7 meters away
        val nandhiniPoint = GeoPoint(12.971650, 77.594650)
        val nandhiniSample = LocationSample(point = nandhiniPoint, accuracyM = 4.0f, timestampMs = System.currentTimeMillis())
        nandhiniBackend.pushLocation(created.coupleId, nandhiniUid, nandhiniSample)

        // Dev observes updated state with Nandhini's location
        val devObserved = withTimeout(5_000) {
            devBackend.observeCouple(created.coupleId, devUid).first { it?.partner?.lastLocation != null }
        }

        assertNotNull(devObserved?.partner?.lastLocation)
        val receivedNandhiniLoc = devObserved!!.partner!!.lastLocation!!.point
        assertEquals(nandhiniPoint.lat, receivedNandhiniLoc.lat, 0.000001)
        assertEquals(nandhiniPoint.lng, receivedNandhiniLoc.lng, 0.000001)

        // Compute physical proximity using DistanceMath
        val distance = DistanceMath.haversineM(devPoint, receivedNandhiniLoc)
        assertTrue("Co-located distance must be sub-10m (was $distance)", distance < 10.0)
        val formatted = DistanceMath.formatDistance(distance)
        assertEquals("8 m", formatted)
    }

    @Test
    fun testSettingsAndSharingSync() = runBlocking {
        val devUid = devBackend.ensureAuth()
        val created = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(created.code, MemberInit(name = "Nandhini", colorIndex = 5))

        // Change radius to 500m
        devBackend.setRadius(created.coupleId, 500)
        val stateAfterRadius = withTimeout(5_000) {
            nandhiniBackend.observeCouple(created.coupleId, nandhiniUid).first { it?.radiusM == 500 }
        }
        assertEquals(500, stateAfterRadius?.radiusM)

        // Dev disables sharing
        devBackend.setSharing(created.coupleId, devUid, false)
        val stateAfterSharing = withTimeout(5_000) {
            nandhiniBackend.observeCouple(created.coupleId, nandhiniUid).first { it?.partner?.sharingOn == false }
        }
        assertFalse(stateAfterSharing!!.partner!!.sharingOn)

        // Dev updates profile name
        devBackend.updateMe(created.coupleId, devUid, "Dev S.", 2)
        val stateAfterProfile = withTimeout(5_000) {
            nandhiniBackend.observeCouple(created.coupleId, nandhiniUid).first { it?.partner?.name == "Dev S." }
        }
        assertEquals("Dev S.", stateAfterProfile?.partner?.name)
        assertEquals(2, stateAfterProfile?.partner?.colorIndex)
    }

    @Test(expected = PairingException::class)
    fun testJoinWithInvalidCodeThrowsPairingException(): Unit = runBlocking {
        nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple("INVALID", MemberInit("Nandhini", 5))
    }

    @Test(expected = PairingException::class)
    fun testJoinFullCoupleThrowsPairingException(): Unit = runBlocking {
        devBackend.ensureAuth()
        val created = devBackend.createCouple(MemberInit("Dev", 0))

        nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(created.code, MemberInit("Nandhini", 5))

        // Third client attempts to join
        val thirdBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        thirdBackend.ensureAuth()
        thirdBackend.joinCouple(created.code, MemberInit("Third", 1))
    }
}
