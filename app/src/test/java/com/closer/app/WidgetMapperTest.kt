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
    }

    @Test fun `fromState no partner location gives null distance`() {
        val me = makeProfile("me", 28.644, 77.216)
        val partner = MemberProfile("them", "Partner", 1, true, lastLocation = null)
        val state = CoupleState("c1", "ABC123", 200, me, partner)
        val snap = WidgetMapper.fromState(state)
        assertNull(snap.distanceM)
    }
}
