package com.closer.app.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.closer.app.R
import com.closer.app.data.ConnectionStatus
import com.closer.app.data.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Empirical Adversarial Challenger: Multi-Size Widget Layout & Status Bounds.
 *
 * Verifies WidgetRenderer with all 4 dimensions (1x2, 1x3, 2x3, 4x3) across all 5
 * connection statuses (Live, Connected, Nearby, Paused, Last seen).
 * Crucially applies the RemoteViews hierarchy to an actual View container in Robolectric
 * to ensure that NO invalid view ID exceptions, resource missing crashes, or inflation
 * errors ever occur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChallengerWidgetAdversarialTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    // =========================================================================
    // 3. MULTI-SIZE WIDGET LAYOUT & STATUS BOUNDS
    // =========================================================================

    @Test
    fun testAllFourDimensionsAcrossAllFiveConnectionStatusesWithRemoteViewsApplication() {
        val now = System.currentTimeMillis()
        val allSizes = listOf(
            WidgetSize.SIZE_1x2,
            WidgetSize.SIZE_1x3,
            WidgetSize.SIZE_2x3,
            WidgetSize.SIZE_4x3,
        )

        val testCases = listOf(
            // 1. Live status
            "Live" to WidgetSnapshot(
                paired = true,
                paused = false,
                isLive = true,
                nearby = false,
                distanceM = 150.0,
                myInitial = "D",
                partnerInitial = "N",
                partnerUpdatedAtMs = now - 5_000L,
            ),
            // 2. Nearby status
            "Nearby" to WidgetSnapshot(
                paired = true,
                paused = false,
                isLive = false,
                nearby = true,
                distanceM = 5.0,
                myInitial = "D",
                partnerInitial = "N",
                partnerUpdatedAtMs = now - 10_000L,
            ),
            // 3. Connected status (fresh fix, not live/nearby)
            "Connected" to WidgetSnapshot(
                paired = true,
                paused = false,
                isLive = false,
                nearby = false,
                distanceM = 2500.0,
                myInitial = "D",
                partnerInitial = "N",
                partnerUpdatedAtMs = now - 2 * 60_000L,
            ),
            // 4. Paused status (explicit privacy pause)
            "Paused" to WidgetSnapshot(
                paired = true,
                paused = true,
                isLive = true,
                nearby = true,
                distanceM = 40.0,
                myInitial = "D",
                partnerInitial = "N",
                partnerUpdatedAtMs = now - 1_000L,
            ),
            // 5. Last seen status (stale > 15 min)
            "LastSeen" to WidgetSnapshot(
                paired = true,
                paused = false,
                isLive = false,
                nearby = false,
                distanceM = 8000.0,
                myInitial = "D",
                partnerInitial = "N",
                partnerUpdatedAtMs = now - 25 * 60_000L,
            ),
        )

        for ((statusName, snapshot) in testCases) {
            // Verify connectionStatus resolves correctly
            val resolvedStatus = snapshot.connectionStatus(now)
            when (statusName) {
                "Live" -> assertEquals(ConnectionStatus.Live, resolvedStatus)
                "Nearby" -> assertEquals(ConnectionStatus.Nearby, resolvedStatus)
                "Connected" -> assertEquals(ConnectionStatus.Connected, resolvedStatus)
                "Paused" -> assertEquals(ConnectionStatus.Paused, resolvedStatus)
                "LastSeen" -> assertEquals(ConnectionStatus.LastSeen, resolvedStatus)
            }

            for (size in allSizes) {
                val rv = WidgetRenderer.remoteViews(context, snapshot, size)
                assertNotNull("RemoteViews must not be null for $size / $statusName", rv)

                // CRITICAL: Apply RemoteViews into actual View hierarchy
                val container = FrameLayout(context)
                val inflated = rv.apply(context, container)
                assertNotNull("Applied view must not be null for $size / $statusName", inflated)

                // Verify root container and core view IDs
                assertNotNull(inflated.findViewById<View>(R.id.widget_root))
                assertNotNull(inflated.findViewById<ImageView>(R.id.lens_me))
                assertNotNull(inflated.findViewById<ImageView>(R.id.lens_partner))
                assertNotNull(inflated.findViewById<ImageView>(R.id.bead))
                assertNotNull(inflated.findViewById<View>(R.id.bridge_line))
                assertNotNull(inflated.findViewById<TextView>(R.id.footer))

                // Verify layout-specific view IDs
                when (size) {
                    WidgetSize.SIZE_1x2 -> {
                        // 1x2 does NOT have status_chip or distance_readout
                        assertNull("SIZE_1x2 must not contain status_chip", inflated.findViewById<View>(R.id.status_chip))
                        assertNull("SIZE_1x2 must not contain distance_readout", inflated.findViewById<View>(R.id.distance_readout))
                    }
                    WidgetSize.SIZE_1x3, WidgetSize.SIZE_2x3 -> {
                        // 1x3 and 2x3 have status_chip, but NOT distance_readout
                        val statusChip = inflated.findViewById<TextView>(R.id.status_chip)
                        assertNotNull("SIZE $size must contain status_chip", statusChip)
                        assertEquals(resolvedStatus.displayLabel, statusChip.text.toString())
                        assertNull("SIZE $size must not contain distance_readout", inflated.findViewById<View>(R.id.distance_readout))
                    }
                    WidgetSize.SIZE_4x3 -> {
                        // 4x3 has BOTH status_chip and distance_readout
                        val statusChip = inflated.findViewById<TextView>(R.id.status_chip)
                        assertNotNull("SIZE_4x3 must contain status_chip", statusChip)
                        assertEquals(resolvedStatus.displayLabel, statusChip.text.toString())

                        val readout = inflated.findViewById<TextView>(R.id.distance_readout)
                        assertNotNull("SIZE_4x3 must contain distance_readout", readout)
                        assertTrue("Distance readout must not be empty", readout.text.isNotEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun testExtremeBoundaryWidgetStatesAppliedSafely() {
        val extremeCases = listOf(
            // Unpaired
            WidgetSnapshot(paired = false, distanceM = null, myInitial = "D", partnerInitial = "?"),
            // Refreshing
            WidgetSnapshot(paired = true, refreshing = true, distanceM = 100.0, myInitial = "D", partnerInitial = "N"),
            // Sub-meter co-location (45 cm)
            WidgetSnapshot(paired = true, distanceM = 0.45, myInitial = "D", partnerInitial = "N"),
            // Zero meters co-location (0 cm)
            WidgetSnapshot(paired = true, distanceM = 0.0, myInitial = "D", partnerInitial = "N"),
            // Distant separation (68 km)
            WidgetSnapshot(paired = true, distanceM = 67500.0, myInitial = "D", partnerInitial = "N"),
            // Null distance (no fix)
            WidgetSnapshot(paired = true, distanceM = null, myInitial = "D", partnerInitial = "N"),
            // Emoji initials
            WidgetSnapshot(paired = true, distanceM = 50.0, myInitial = "💖", partnerInitial = "⚡"),
            // Empty initials
            WidgetSnapshot(paired = true, distanceM = 50.0, myInitial = "", partnerInitial = ""),
            // Long initials
            WidgetSnapshot(paired = true, distanceM = 50.0, myInitial = "Alexander", partnerInitial = "Nandhini"),
        )

        for (snap in extremeCases) {
            for (size in WidgetSize.values()) {
                val rv = WidgetRenderer.remoteViews(context, snap, size)
                val container = FrameLayout(context)
                val view = rv.apply(context, container)
                assertNotNull("Applied view must not be null for size $size with snapshot $snap", view)
            }
        }
    }
}
