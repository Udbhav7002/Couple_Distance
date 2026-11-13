package com.closer.app.data

/** A rounded GPS point. */
data class GeoPoint(val lat: Double, val lng: Double)

data class LocationSample(
    val point: GeoPoint,
    val accuracyM: Float,
    val timestampMs: Long,
    val batteryPct: Int? = null,
) {
    val battery: Int? get() = batteryPct
}

data class MemberProfile(
    val uid: String,
    val name: String,
    val colorIndex: Int,
    val sharingOn: Boolean = true,
    val lastLocation: LocationSample? = null,
    val batteryPct: Int? = null,
) {
    val battery: Int? get() = batteryPct ?: lastLocation?.batteryPct
    val initial: String
        get() = name.trim().take(1).uppercase().ifEmpty { "?" }
}

data class CoupleState(
    val coupleId: String,
    val code: String,
    val radiusM: Int,
    val me: MemberProfile,
    val partner: MemberProfile?,
) {
    val paired: Boolean get() = partner != null
}

enum class ShareMode { ECO, LIVE }

enum class ConnectionStatus(val label: String, val displayLabel: String) {
    Live("Live", "Live ⚡"),
    Connected("Connected", "Connected"),
    Nearby("Nearby", "Nearby 💞"),
    Paused("Paused", "Paused"),
    LastSeen("Last seen", "Last seen");

    companion object {
        fun resolve(snapshot: WidgetSnapshot, nowMs: Long = System.currentTimeMillis()): ConnectionStatus = when {
            snapshot.paused -> Paused
            snapshot.isLive -> Live
            snapshot.nearby -> Nearby
            snapshot.partnerUpdatedAtMs == null || (nowMs - snapshot.partnerUpdatedAtMs > 15 * 60 * 1000L) -> LastSeen
            else -> Connected
        }
    }
}

/** Everything the spectacles widget needs to draw itself, kept fresh in prefs for cold starts. */
data class WidgetSnapshot(
    val paired: Boolean = false,
    val myInitial: String = "?",
    val myColorIndex: Int = 0,
    val partnerInitial: String = "?",
    val partnerColorIndex: Int = 5,
    val distanceM: Double? = null,
    val myAccuracyM: Float? = null,
    val myUpdatedAtMs: Long? = null,
    val partnerUpdatedAtMs: Long? = null,
    val nearby: Boolean = false,
    val radiusM: Int = 200,
    val refreshing: Boolean = false,
    val paused: Boolean = false,
    val isLive: Boolean = false,
) {
    fun resolveStatus(nowMs: Long = System.currentTimeMillis()): String =
        ConnectionStatus.resolve(this, nowMs).label

    fun connectionStatus(nowMs: Long = System.currentTimeMillis()): ConnectionStatus =
        ConnectionStatus.resolve(this, nowMs)

    val statusText: String
        get() = resolveStatus()
}
