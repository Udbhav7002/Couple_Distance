package com.closer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.closer.app.data.SnapshotStore
import com.closer.app.refresh.Refresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base provider for all Closer spectacles widget sizes (1x2, 1x3, 2x3, 4x3).
 * Handles periodic updates, option resizes, and asynchronous manual refresh broadcasts.
 */
abstract class BaseSpectaclesWidgetProvider(
    private val defaultSize: WidgetSize,
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        val snapshot = SnapshotStore(context).load()
        for (id in appWidgetIds) {
            val options = mgr.getAppWidgetOptions(id)
            val size = WidgetUpdater.resolveSize(options, defaultSize)
            mgr.updateAppWidget(id, WidgetRenderer.remoteViews(context, snapshot, size))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        val snapshot = SnapshotStore(context).load()
        val size = WidgetUpdater.resolveSize(newOptions, defaultSize)
        mgr.updateAppWidget(appWidgetId, WidgetRenderer.remoteViews(context, snapshot, size))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Refresher.refresh(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.closer.app.WIDGET_REFRESH"

        fun refreshPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, SpectaclesWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun openAppPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
