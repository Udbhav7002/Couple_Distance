package com.closer.app.data

/** A rounded GPS point. */
data class GeoPoint(val lat: Double, val lng: Double)

data class LocationSample(
    val point: GeoPoint,
    val accuracyM: Float,
    val timestampMs: Long,
)

data class MemberProfile(
    val uid: String,
    val name: String,
    val colorIndex: Int,
    val sharingOn: Boolean = true,
    val lastLocation: LocationSample? = null,
) {
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
)
