package com.closer.app.notify

import android.content.Context
import com.closer.app.di.AppContainer
import com.closer.app.location.DistanceMath
import kotlinx.coroutines.flow.first

/** Edge-triggered nearby alert: fires once when crossing into the radius. */
object ProximityChecker {

    private const val KEY = "was_nearby"

    suspend fun check(c: AppContainer, state: com.closer.app.data.CoupleState?, snap: com.closer.app.data.WidgetSnapshot) {
        if (state == null || snap.distanceM == null) return
        val prefs = c.appContext.getSharedPreferences("closer_state", Context.MODE_PRIVATE)
        val was = prefs.getBoolean(KEY, false)
        val isNear = snap.nearby

        if (isNear && !was) {
            val alertsOn = c.settings.nearAlerts.first()
            val from = c.settings.quietFrom.first()
            val to = c.settings.quietTo.first()
            val partner = state.partner
            if (alertsOn && partner != null && !c.notifier.isQuietHour(from, to)) {
                c.notifier.proximity(partner.name, DistanceMath.formatDistance(snap.distanceM))
            }
        }
        prefs.edit().putBoolean(KEY, isNear).apply()
    }
}
