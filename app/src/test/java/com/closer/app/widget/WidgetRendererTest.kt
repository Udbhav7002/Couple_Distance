package com.closer.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import com.closer.app.R
import com.closer.app.data.SnapshotStore
import com.closer.app.data.WidgetSnapshot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetRendererTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `render SIZE_1x2 generates remote views with correct layout`() {
        val snap = WidgetSnapshot(
            paired = true,
            myInitial = "D",
            partnerInitial = "N",
            distanceM = 420.0,
            isLive = true,
        )
        val rv = WidgetRenderer.remoteViews(context, snap, WidgetSize.SIZE_1x2)
        assertNotNull(rv)
        assertEquals(R.layout.widget_spectacles_1x2, rv.layoutId)
    }

    @Test
    fun `render SIZE_1x3 generates remote views with correct layout`() {
        val snap = WidgetSnapshot(
            paired = true,
            myInitial = "D",
            partnerInitial = "N",
            distanceM = 15.0,
            nearby = true,
        )
        val rv = WidgetRenderer.remoteViews(context, snap, WidgetSize.SIZE_1x3)
        assertNotNull(rv)
        assertEquals(R.layout.widget_spectacles_1x3, rv.layoutId)
    }

    @Test
    fun `render SIZE_2x3 generates remote views with correct layout`() {
        val snap = WidgetSnapshot(
            paired = true,
            myInitial = "D",
            partnerInitial = "N",
            distanceM = 1200.0,
            partnerUpdatedAtMs = System.currentTimeMillis(),
        )
        val rv = WidgetRenderer.remoteViews(context, snap, WidgetSize.SIZE_2x3)
        assertNotNull(rv)
        assertEquals(R.layout.widget_spectacles_2x3, rv.layoutId)
    }

    @Test
    fun `render SIZE_4x3 generates hero remote views with distance readout and status`() {
        val snap = WidgetSnapshot(
            paired = true,
            myInitial = "D",
            partnerInitial = "N",
            distanceM = 3450.0,
            isLive = true,
        )
        val rv = WidgetRenderer.remoteViews(context, snap, WidgetSize.SIZE_4x3)
        assertNotNull(rv)
        assertEquals(R.layout.widget_spectacles_4x3, rv.layoutId)
    }

    @Test
    fun `render backwards compatibility with compact flag`() {
        val snap = WidgetSnapshot(paired = true, distanceM = 50.0)
        val rvCompact = WidgetRenderer.remoteViews(context, snap, compact = true)
        assertEquals(R.layout.widget_spectacles_2x3, rvCompact.layoutId)

        val rvFull = WidgetRenderer.remoteViews(context, snap, compact = false)
        assertEquals(R.layout.widget_spectacles_4x3, rvFull.layoutId)
    }

    @Test
    fun `render all 5 connection statuses across all layouts without exceptions`() {
        val now = System.currentTimeMillis()
        val statuses = listOf(
            // Paused
            WidgetSnapshot(paired = true, paused = true, isLive = true, nearby = true),
            // Live
            WidgetSnapshot(paired = true, paused = false, isLive = true, nearby = false, partnerUpdatedAtMs = now),
            // Nearby
            WidgetSnapshot(paired = true, paused = false, isLive = false, nearby = true, distanceM = 80.0),
            // Last seen (stale)
            WidgetSnapshot(paired = true, paused = false, isLive = false, nearby = false, partnerUpdatedAtMs = now - 20 * 60_000L),
            // Connected (fresh)
            WidgetSnapshot(paired = true, paused = false, isLive = false, nearby = false, partnerUpdatedAtMs = now - 2 * 60_000L),
        )

        val sizes = listOf(WidgetSize.SIZE_1x2, WidgetSize.SIZE_1x3, WidgetSize.SIZE_2x3, WidgetSize.SIZE_4x3)

        for (snap in statuses) {
            for (size in sizes) {
                val rv = WidgetRenderer.remoteViews(context, snap, size)
                assertNotNull(rv)
            }
        }
    }

    @Test
    fun `render unpaired and refreshing states without exceptions`() {
        val unpairedSnap = WidgetSnapshot(paired = false, distanceM = null)
        val refreshingSnap = WidgetSnapshot(paired = true, refreshing = true, distanceM = 100.0)

        for (size in WidgetSize.values()) {
            assertNotNull(WidgetRenderer.remoteViews(context, unpairedSnap, size))
            assertNotNull(WidgetRenderer.remoteViews(context, refreshingSnap, size))
        }
    }

    @Test
    fun `resolveSize correctly infers widget size from bundle dimensions`() {
        // 1x2: minW < 100, minH < 150
        val b1x2 = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 60)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        }
        assertEquals(WidgetSize.SIZE_1x2, WidgetUpdater.resolveSize(b1x2, WidgetSize.SIZE_4x3))

        // 1x3: minW < 100, minH >= 150
        val b1x3 = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 60)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        }
        assertEquals(WidgetSize.SIZE_1x3, WidgetUpdater.resolveSize(b1x3, WidgetSize.SIZE_4x3))

        // 2x3: 100 <= minW < 200
        val b2x3 = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        }
        assertEquals(WidgetSize.SIZE_2x3, WidgetUpdater.resolveSize(b2x3, WidgetSize.SIZE_4x3))

        // 4x3: minW >= 200
        val b4x3 = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        }
        assertEquals(WidgetSize.SIZE_4x3, WidgetUpdater.resolveSize(b4x3, WidgetSize.SIZE_1x2))

        // null / empty fallback
        assertEquals(WidgetSize.SIZE_1x2, WidgetUpdater.resolveSize(null, WidgetSize.SIZE_1x2))
        assertEquals(WidgetSize.SIZE_2x3, WidgetUpdater.resolveSize(Bundle(), WidgetSize.SIZE_2x3))
    }

    @Test
    fun `snapshotStore serializes and restores isLive`() {
        val store = SnapshotStore(context)
        val snapLive = WidgetSnapshot(paired = true, distanceM = 300.0, isLive = true)
        store.save(snapLive)
        val loadedLive = store.load()
        assertTrue(loadedLive.isLive)
        assertEquals(300.0, loadedLive.distanceM!!, 0.001)

        val snapEco = WidgetSnapshot(paired = true, distanceM = 300.0, isLive = false)
        store.save(snapEco)
        val loadedEco = store.load()
        assertFalse(loadedEco.isLive)
    }
}
