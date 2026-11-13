package com.closer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.closer.app.data.SnapshotStore
import com.closer.app.data.WidgetSnapshot

/**
 * Pushes widget snapshots across all four supported widget sizes
 * (1x2, 1x3, 2x3, 4x3) to keep home screen spectacles up to date.
 */
object WidgetUpdater {

    fun renderAll(context: Context, snapshot: WidgetSnapshot = SnapshotStore(context).load()) {
        val mgr = AppWidgetManager.getInstance(context) ?: return

        updateProvider(context, mgr, SpectaclesWidgetProvider::class.java, snapshot, WidgetSize.SIZE_4x3)
        updateProvider(context, mgr, SpectaclesWidgetProvider2x3::class.java, snapshot, WidgetSize.SIZE_2x3)
        updateProvider(context, mgr, SpectaclesWidgetProvider1x3::class.java, snapshot, WidgetSize.SIZE_1x3)
        updateProvider(context, mgr, SpectaclesWidgetProvider1x2::class.java, snapshot, WidgetSize.SIZE_1x2)
    }

    private fun updateProvider(
        context: Context,
        mgr: AppWidgetManager,
        providerClass: Class<out AppWidgetProvider>,
        snapshot: WidgetSnapshot,
        defaultSize: WidgetSize,
    ) {
        val cn = ComponentName(context, providerClass)
        val ids = mgr.getAppWidgetIds(cn) ?: return
        for (id in ids) {
            val options = mgr.getAppWidgetOptions(id)
            val size = resolveSize(options, defaultSize)
            mgr.updateAppWidget(id, WidgetRenderer.remoteViews(context, snapshot, size))
        }
    }

    fun resolveSize(options: Bundle?, defaultSize: WidgetSize): WidgetSize {
        if (options == null) return defaultSize
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (minW <= 0 && minH <= 0) return defaultSize

        return when {
            minW in 1..99 -> if (minH >= 150) WidgetSize.SIZE_1x3 else WidgetSize.SIZE_1x2
            minW in 100..199 -> WidgetSize.SIZE_2x3
            minW >= 200 -> WidgetSize.SIZE_4x3
            else -> defaultSize
        }
    }
}
