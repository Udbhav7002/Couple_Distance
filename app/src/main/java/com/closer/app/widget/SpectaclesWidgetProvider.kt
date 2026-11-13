package com.closer.app.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 4x3 Hero spectacles widget provider.
 * Extends [BaseSpectaclesWidgetProvider] for the largest hero display with status and distance readout.
 */
class SpectaclesWidgetProvider : BaseSpectaclesWidgetProvider(WidgetSize.SIZE_4x3) {

    companion object {
        const val ACTION_REFRESH = BaseSpectaclesWidgetProvider.ACTION_REFRESH

        fun refreshPendingIntent(context: Context): PendingIntent =
            BaseSpectaclesWidgetProvider.refreshPendingIntent(context)

        fun openAppPendingIntent(context: Context): PendingIntent =
            BaseSpectaclesWidgetProvider.openAppPendingIntent(context)
    }
}

/** Alias for explicit 4x3 provider references. */
typealias SpectaclesWidgetProvider4x3 = SpectaclesWidgetProvider

/** Re-renders widgets from the saved snapshot after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WidgetUpdater.renderAll(context)
        }
    }
}
