package com.closer.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.closer.app.data.SnapshotStore
import com.closer.app.data.WidgetSnapshot

/** Pushes a snapshot to every placed widget (4×2 full and compact 2×2). */
object WidgetUpdater {

    fun renderAll(context: Context, snapshot: WidgetSnapshot = SnapshotStore(context).load()) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val cn = ComponentName(context, SpectaclesWidgetProvider::class.java)
        for (id in mgr.getAppWidgetIds(cn)) {
            val minW = mgr.getAppWidgetOptions(id)
                ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
            val compact = minW in 1..199
            mgr.updateAppWidget(id, WidgetRenderer.remoteViews(context, snapshot, compact))
        }
    }
}
