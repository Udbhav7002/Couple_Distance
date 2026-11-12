package com.closer.app.widget

import com.closer.app.data.CoupleState
import com.closer.app.data.WidgetSnapshot
import com.closer.app.location.DistanceMath

object WidgetMapper {

    /** CoupleState -> everything the widget needs. */
    fun fromState(state: CoupleState?, refreshing: Boolean = false): WidgetSnapshot {
        if (state == null) return WidgetSnapshot(paired = false, refreshing = refreshing)

        val me = state.me
        val partner = state.partner
        val mine = me.lastLocation
        val theirs = partner?.lastLocation
        val distance = if (mine != null && theirs != null) {
            DistanceMath.haversineM(mine.point, theirs.point)
        } else null

        return WidgetSnapshot(
            paired = true,
            myInitial = me.initial,
            myColorIndex = me.colorIndex,
            partnerInitial = partner?.initial ?: "?",
            partnerColorIndex = partner?.colorIndex ?: 5,
            distanceM = distance,
            myAccuracyM = mine?.accuracyM,
            myUpdatedAtMs = mine?.timestampMs,
            partnerUpdatedAtMs = theirs?.timestampMs,
            nearby = distance != null && distance <= state.radiusM,
            radiusM = state.radiusM,
            refreshing = refreshing,
            paused = !me.sharingOn,
        )
    }
}
