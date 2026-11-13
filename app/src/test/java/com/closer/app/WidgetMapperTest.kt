package com.closer.app

import com.closer.app.data.CoupleState
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.data.MemberProfile
import com.closer.app.widget.WidgetMapper
import org.junit.Assert.*
import org.junit.Test

class WidgetMapperTest {

    private fun makeProfile(uid: String, lat: Double, lng: Double, name: String = "A", sharing: Boolean = true) =
        MemberProfile(
            uid = uid,
            name = name,
            colorIndex = 0,
            sharingOn = sharing,
            lastLocation = LocationSample(GeoPoint(lat, lng), 10f, System.currentTimeMillis()),
        )

    @Test fun `fromState null returns unpaired snapshot`() {
        val snap = WidgetMapper.fromState(null)
        assertFalse(snap.paired)
        assertNull(snap.distanceM)
    }

    @Test fun `fromState paired with locations computes distance`() {
        val me = makeProfile("me", 28.644, 77.216)
        val partner = makeProfile("them", 28.650, 77.216)
        val state = CoupleState("c1", "ABC123", 200, me, partner)
        val snap = WidgetMapper.fromState(state)
        assertTrue(snap.paired)
        assertNotNull(snap.distanceM)
        // ~668 m between those coords
        assertTrue(snap.distanceM!! > 500 && snap.distanceM!! < 900)
    }

    @Test fun `fromState nearby flag set when within radius`() {
        val me = makeProfile("me", 28.644, 77.216)
        val partner = makeProfile("them", 28.6441, 77.216) // ~11m apart
        val state = CoupleState("c1", "ABC123", 200, me, partner)
        val snap = WidgetMapper.fromState(state)
        assertTrue(snap.nearby)
    }

    @Test fun `fromState not nearby when outside radius`() {
        val me = makeProfile("me", 28.644, 77.216)
        val partner = makeProfile("them", 28.650, 77.216) // ~668m apart
        val state = CoupleState("c1", "ABC123", 200, me, partner)
        val snap = WidgetMapper.fromState(state)
        assertFalse(snap.nearby)
    }

    @Test fun `fromState paused flag reflects sharingOn`() {
        val me = makeProfile("me", 28.644, 77.216, sharing = false)
        val state = CoupleState("c1", "ABC123", 200, me, null)
        val snap = WidgetMapper.fromState(state)
        assertTrue(snap.paused)

        val meSharing = makeProfile("me", 28.644, 77.216, sharing = true)
        val partnerPaused = makeProfile("them", 28.644, 77.216, sharing = false)
        val statePartnerPaused = CoupleState("c1", "ABC123", 200, meSharing, partnerPaused)
        val snapPartnerPaused = WidgetMapper.fromState(statePartnerPaused)
        assertTrue(snapPartnerPaused.paused)
    }

    @Test fun `fromState no partner location gives null distance`() {
        val me = makeProfile("me", 28.644, 77.216)
        val partner = MemberProfile("them", "Partner", 1, true, lastLocation = null)
        val state = CoupleState("c1", "ABC123", 200, me, partner)
        val snap = WidgetMapper.fromState(state)
        assertNull(snap.distanceM)
    }

    @Test fun `fromState maps isLive true and false correctly`() {
        val me = makeProfile("me", 28.644, 77.216)
        val state = CoupleState("c1", "ABC123", 200, me, null)
        val snapLive = WidgetMapper.fromState(state, isLive = true)
        assertTrue(snapLive.isLive)

        val snapEco = WidgetMapper.fromState(state, isLive = false)
        assertFalse(snapEco.isLive)

        val snapNullLive = WidgetMapper.fromState(null, isLive = true)
        assertTrue(snapNullLive.isLive)
    }

    @Test fun `resolveStatus paused takes highest precedence`() {
        val now = 1_000_000L
        val snap = com.closer.app.data.WidgetSnapshot(
            paused = true,
            isLive = true,
            nearby = true,
            partnerUpdatedAtMs = now,
        )
        assertEquals("Paused", snap.resolveStatus(now))
        assertEquals(com.closer.app.data.ConnectionStatus.Paused, snap.connectionStatus(now))
        assertEquals("Paused", snap.statusText)
    }

    @Test fun `resolveStatus live takes precedence over nearby and recent`() {
        val now = 1_000_000L
        val snap = com.closer.app.data.WidgetSnapshot(
            paused = false,
            isLive = true,
            nearby = true,
            partnerUpdatedAtMs = now,
        )
        assertEquals("Live", snap.resolveStatus(now))
        assertEquals(com.closer.app.data.ConnectionStatus.Live, snap.connectionStatus(now))
    }

    @Test fun `resolveStatus nearby when not paused and not live`() {
        val now = 1_000_000L
        val snap = com.closer.app.data.WidgetSnapshot(
            paused = false,
            isLive = false,
            nearby = true,
            partnerUpdatedAtMs = now,
        )
        assertEquals("Nearby", snap.resolveStatus(now))
        assertEquals(com.closer.app.data.ConnectionStatus.Nearby, snap.connectionStatus(now))
    }

    @Test fun `resolveStatus last seen when partnerUpdatedAtMs is null`() {
        val now = 1_000_000L
        val snap = com.closer.app.data.WidgetSnapshot(
            paused = false,
            isLive = false,
            nearby = false,
            partnerUpdatedAtMs = null,
        )
        assertEquals("Last seen", snap.resolveStatus(now))
        assertEquals(com.closer.app.data.ConnectionStatus.LastSeen, snap.connectionStatus(now))
    }

    @Test fun `resolveStatus last seen when partner timestamp older than 15 minutes`() {
        val now = 2_000_000L
        val sixteenMinutesAgo = now - (16 * 60 * 1000L)
        val snap = com.closer.app.data.WidgetSnapshot(
            paused = false,
            isLive = false,
            nearby = false,
            partnerUpdatedAtMs = sixteenMinutesAgo,
        )
        assertEquals("Last seen", snap.resolveStatus(now))
        assertEquals(com.closer.app.data.ConnectionStatus.LastSeen, snap.connectionStatus(now))
    }

    @Test fun `resolveStatus connected when partner timestamp within 15 minutes`() {
        val now = 2_000_000L
        val fiveMinutesAgo = now - (5 * 60 * 1000L)
        val snap = com.closer.app.data.WidgetSnapshot(
            paused = false,
            isLive = false,
            nearby = false,
            partnerUpdatedAtMs = fiveMinutesAgo,
        )
        assertEquals("Connected", snap.resolveStatus(now))
        assertEquals(com.closer.app.data.ConnectionStatus.Connected, snap.connectionStatus(now))
    }
}
