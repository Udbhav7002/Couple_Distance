package com.closer.app.backend.relay

import com.closer.app.data.CoupleState
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.data.MemberProfile
import org.json.JSONObject

object RelayJson {

    fun coupleStateToJson(state: CoupleState): JSONObject = JSONObject().apply {
        put("coupleId", state.coupleId)
        put("code", state.code)
        put("radiusM", state.radiusM)
        put("me", memberToJson(state.me))
        if (state.partner != null) {
            put("partner", memberToJson(state.partner))
        } else {
            put("partner", JSONObject.NULL)
        }
    }

    fun coupleStateFromJson(j: JSONObject): CoupleState = CoupleState(
        coupleId = j.getString("coupleId"),
        code = j.getString("code"),
        radiusM = j.optInt("radiusM", 200),
        me = memberFromJson(j.getJSONObject("me")),
        partner = if (j.has("partner") && !j.isNull("partner")) {
            memberFromJson(j.getJSONObject("partner"))
        } else null,
    )

    fun memberToJson(m: MemberProfile): JSONObject = JSONObject().apply {
        put("uid", m.uid)
        put("name", m.name)
        put("colorIndex", m.colorIndex)
        put("sharingOn", m.sharingOn)
        val batt = m.battery
        if (batt != null) {
            put("batteryPct", batt)
            put("battery", batt)
        }
        val loc = m.lastLocation
        if (loc != null) {
            put("lastLocation", locationSampleToJson(loc))
        } else {
            put("lastLocation", JSONObject.NULL)
        }
    }

    fun memberFromJson(j: JSONObject): MemberProfile {
        val lastLocation = if (j.has("lastLocation") && !j.isNull("lastLocation")) {
            locationSampleFromJson(j.getJSONObject("lastLocation"))
        } else if (j.has("loc") && !j.isNull("loc")) {
            locationSampleFromJson(j.getJSONObject("loc"))
        } else null

        val batteryPct = if (j.has("batteryPct") && !j.isNull("batteryPct")) {
            j.optInt("batteryPct")
        } else if (j.has("battery") && !j.isNull("battery")) {
            j.optInt("battery")
        } else lastLocation?.batteryPct

        return MemberProfile(
            uid = j.getString("uid"),
            name = j.getString("name"),
            colorIndex = j.optInt("colorIndex", 0),
            sharingOn = j.optBoolean("sharingOn", true),
            lastLocation = lastLocation,
            batteryPct = batteryPct,
        )
    }

    fun locationSampleToJson(s: LocationSample): JSONObject = JSONObject().apply {
        put("point", JSONObject().apply {
            put("lat", s.point.lat)
            put("lng", s.point.lng)
        })
        put("accuracyM", s.accuracyM.toDouble())
        put("timestampMs", s.timestampMs)
        if (s.batteryPct != null) {
            put("batteryPct", s.batteryPct)
            put("battery", s.batteryPct)
        }
    }

    fun locationSampleFromJson(j: JSONObject): LocationSample {
        val point = if (j.has("point")) {
            val p = j.getJSONObject("point")
            GeoPoint(p.getDouble("lat"), p.getDouble("lng"))
        } else {
            GeoPoint(j.getDouble("lat"), j.getDouble("lng"))
        }
        val accuracy = if (j.has("accuracyM")) {
            j.getDouble("accuracyM").toFloat()
        } else if (j.has("acc")) {
            j.getDouble("acc").toFloat()
        } else {
            5f
        }
        val ts = if (j.has("timestampMs")) {
            j.getLong("timestampMs")
        } else if (j.has("ts")) {
            j.getLong("ts")
        } else {
            System.currentTimeMillis()
        }
        val batteryPct = if (j.has("batteryPct") && !j.isNull("batteryPct")) {
            j.optInt("batteryPct")
        } else if (j.has("battery") && !j.isNull("battery")) {
            j.optInt("battery")
        } else null

        return LocationSample(
            point = point,
            accuracyM = accuracy,
            timestampMs = ts,
            batteryPct = batteryPct,
        )
    }
}
