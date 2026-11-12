package com.closer.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Widget snapshots live in plain SharedPreferences: the widget provider must
 * render synchronously on cold start / reboot, without spinning up DataStore.
 */
class SnapshotStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("closer_widget", Context.MODE_PRIVATE)

    fun save(s: WidgetSnapshot) {
        prefs.edit().putString("snapshot", toJson(s).toString()).apply()
    }

    fun load(): WidgetSnapshot = prefs.getString("snapshot", null)
        ?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
        ?: WidgetSnapshot()

    private fun toJson(s: WidgetSnapshot) = JSONObject().apply {
        put("paired", s.paired)
        put("myInitial", s.myInitial)
        put("myColor", s.myColorIndex)
        put("partnerInitial", s.partnerInitial)
        put("partnerColor", s.partnerColorIndex)
        s.distanceM?.let { put("distanceM", it) }
        s.myUpdatedAtMs?.let { put("myAt", it) }
        s.partnerUpdatedAtMs?.let { put("partnerAt", it) }
        put("nearby", s.nearby)
        put("radiusM", s.radiusM)
        put("refreshing", s.refreshing)
        put("paused", s.paused)
    }

    private fun fromJson(j: JSONObject) = WidgetSnapshot(
        paired = j.optBoolean("paired", false),
        myInitial = j.optString("myInitial", "?"),
        myColorIndex = j.optInt("myColor", 0),
        partnerInitial = j.optString("partnerInitial", "?"),
        partnerColorIndex = j.optInt("partnerColor", 5),
        distanceM = if (j.has("distanceM")) j.getDouble("distanceM") else null,
        myUpdatedAtMs = if (j.has("myAt")) j.getLong("myAt") else null,
        partnerUpdatedAtMs = if (j.has("partnerAt")) j.getLong("partnerAt") else null,
        nearby = j.optBoolean("nearby", false),
        radiusM = j.optInt("radiusM", 200),
        refreshing = j.optBoolean("refreshing", false),
        paused = j.optBoolean("paused", false),
    )
}
