package com.closer.app

import com.closer.app.backend.MemberInit
import com.closer.app.backend.relay.CodeGenerator
import com.closer.app.backend.relay.LiveRelayBackend
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.location.DistanceMath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end integration test verifying that two independent clients
 * pair and stream coordinates in real time over the global public relay (ntfy.sh).
 */
class NtfyLiveIntegrationTest {

    private val publicRelayUrl = "https://ntfy.sh"

    @Test
    fun testLiveDualDevicePairingAndProximitySyncOverPublicRelay() = runBlocking {
        val clientDev = LiveRelayBackend(explicitBaseUrl = publicRelayUrl)
        val clientNandhini = LiveRelayBackend(explicitBaseUrl = publicRelayUrl)

        // 1. Dev creates couple
        val devState = clientDev.createCouple(MemberInit(name = "Dev", colorIndex = 0))
        assertNotNull(devState.code)
        assertEquals(6, devState.code.length)
        assertEquals("Dev", devState.me.name)
        assertNull("Partner must be null before join", devState.partner)

        // 2. Nandhini joins using the 6-character code
        val nanState = clientNandhini.joinCouple(devState.code, MemberInit(name = "Nandhini", colorIndex = 1))
        assertEquals("Nandhini", nanState.me.name)
        assertNotNull("Nandhini must see Dev as partner", nanState.partner)
        assertEquals("Dev", nanState.partner?.name)

        // 3. Dev observes couple and sees Nandhini attached
        val devObserved = withTimeout(10_000) {
            clientDev.observeCouple(devState.coupleId, devState.me.uid)
                .first { it?.partner != null }
        }
        assertNotNull(devObserved)
        assertEquals("Nandhini", devObserved?.partner?.name)

        // 4. Test Co-Location: Nandhini and Dev are 1.5 meters apart
        val devLocation = LocationSample(GeoPoint(12.971600, 77.594600), 2.0f, System.currentTimeMillis())
        val nanLocation = LocationSample(GeoPoint(12.971614, 77.594600), 2.0f, System.currentTimeMillis())

        clientDev.pushLocation(devState.coupleId, devState.me.uid, devLocation)
        clientNandhini.pushLocation(devState.coupleId, nanState.me.uid, nanLocation)

        // 5. Verify Dev receives Nandhini's location and distance is < 10m
        val updatedDev = withTimeout(10_000) {
            clientDev.observeCouple(devState.coupleId, devState.me.uid)
                .first { it?.partner?.lastLocation != null }
        }
        assertNotNull(updatedDev?.partner?.lastLocation)
        val dist = DistanceMath.haversineM(
            updatedDev!!.me.lastLocation!!.point,
            updatedDev.partner!!.lastLocation!!.point
        )
        assertTrue("Distance must be < 10m for co-located devices, was: $dist", dist < 10.0)
    }
}
