package com.closer.app.refresh

import android.content.Context
import com.closer.app.data.CoupleState
import com.closer.app.data.LocationSample
import com.closer.app.data.Pairing
import com.closer.app.di.AppContainer
import com.closer.app.di.Locator
import com.closer.app.location.DistanceMath
import com.closer.app.notify.ProximityChecker
import com.closer.app.widget.WidgetMapper
import com.closer.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single path that updates everything: fresh GPS -> push mine -> pull theirs
 * -> widget -> proximity alert. Used by the widget tap, the app's refresh button,
 * the periodic worker and Live mode.
 */
object Refresher {

    /** Full refresh with a fresh high-accuracy fix (widget tap, app open, refresh button). */
    suspend fun refresh(context: Context) {
        val c = Locator.init(context)
        val pairing = c.settings.snapshotPairing()
        if (pairing == null) return renderUnpaired(context)

        // Show the pulsing "…" bead immediately.
        c.snapshots.save(c.snapshots.load().copy(refreshing = true))
        WidgetUpdater.renderAll(context)

        val sample = c.locationEngine.freshFix() ?: c.locationEngine.lastKnown()
        sync(context, pairing, sample)
    }

    /** Push a known sample (Live mode / passive / worker) and sync everything down. */
    suspend fun sync(
        context: Context,
        pairing: Pairing? = null,
        sample: LocationSample?,
        isLive: Boolean? = null,
    ) {
        val c = Locator.init(context)
        val p = pairing ?: c.settings.snapshotPairing()
        if (p == null) return renderUnpaired(context)

        val sharingOn = c.settings.sharingOn.first()
        if (sample != null && sharingOn) {
            try {
                c.backend.pushLocation(p.coupleId, p.uid, sample)
            } catch (_: Throwable) {
                // offline — widget still renders from cache below
            }
        }

        val state = fetchState(c, p)

        val liveMode = isLive ?: (c.settings.mode.first() == com.closer.app.data.ShareMode.LIVE)
        val snap = WidgetMapper.fromState(state, isLive = liveMode)
        c.snapshots.save(snap)
        WidgetUpdater.renderAll(context)
        ProximityChecker.check(c, state, snap)
    }

    private suspend fun fetchState(c: AppContainer, p: Pairing): CoupleState? =
        withTimeoutOrNull(15_000) {
            runCatching { c.backend.observeCouple(p.coupleId, p.uid).first() }.getOrNull()
        }

    private suspend fun renderUnpaired(context: Context) {
        val c = Locator.init(context)
        val name = c.settings.myName.first()
        val color = c.settings.myColorIndex.first()
        val liveMode = c.settings.mode.first() == com.closer.app.data.ShareMode.LIVE
        val snap = com.closer.app.data.WidgetSnapshot(
            paired = false,
            myInitial = name.trim().take(1).uppercase().ifEmpty { "?" },
            myColorIndex = color,
            isLive = liveMode,
        )
        c.snapshots.save(snap)
        WidgetUpdater.renderAll(context)
    }
}
