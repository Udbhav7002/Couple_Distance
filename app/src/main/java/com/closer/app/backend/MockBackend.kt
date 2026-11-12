package com.closer.app.backend

import android.content.Context
import com.closer.app.data.CoupleState
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.data.MemberProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

/**
 * In-memory backend used until google-services.json is added. It simulates a
 * partner ("Ava") who drifts around at a configurable distance, so the whole
 * experience — widget, proximity alert, live updates — is demoable on a single
 * emulator. State persists across restarts so the demo survives reinstalls.
 */
class MockBackend(context: Context? = null) : Backend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context?.applicationContext
        ?.getSharedPreferences("closer_mock", Context.MODE_PRIVATE)

    private val couple = MutableStateFlow<CoupleState?>(null)
    private var driftJobActive = false

    // Simulation controls (tuned from the debug panel)
    var partnerName: String = "Ava"
    var partnerSpeedMs: Double = 1.2   // walking speed drift; 0 = frozen
    var enabled: Boolean = true

    init {
        restore()
    }

    override suspend fun ensureAuth(): String = "mock-me"

    override suspend fun createCouple(me: MemberInit): CoupleState {
        val state = CoupleState(
            coupleId = "mock-couple",
            code = generateCode(),
            radiusM = 200,
            me = MemberProfile(uid = "mock-me", name = me.name, colorIndex = me.colorIndex),
            partner = simulatedPartner(),
        )
        set(state)
        startDrift()
        return state
    }

    override suspend fun joinCouple(code: String, me: MemberInit): CoupleState {
        // Mock has a single device: behave like create so the flow is testable.
        return createCouple(me)
    }

    override fun observeCouple(coupleId: String, myUid: String): Flow<CoupleState?> = couple

    override suspend fun pushLocation(coupleId: String, uid: String, sample: LocationSample) {
        val c = couple.value ?: return
        set(
            if (uid == c.me.uid) c.copy(me = c.me.copy(lastLocation = sample))
            else c.copy(partner = c.partner?.copy(lastLocation = sample))
        )
    }

    override suspend fun setSharing(coupleId: String, uid: String, on: Boolean) {
        val c = couple.value ?: return
        set(
            if (uid == c.me.uid) c.copy(me = c.me.copy(sharingOn = on))
            else c.copy(partner = c.partner?.copy(sharingOn = on))
        )
    }

    override suspend fun setRadius(coupleId: String, radiusM: Int) {
        couple.value?.let { set(it.copy(radiusM = radiusM)) }
    }

    override suspend fun updateMe(coupleId: String, uid: String, name: String, colorIndex: Int) {
        couple.value?.let { set(it.copy(me = it.me.copy(name = name, colorIndex = colorIndex))) }
    }

    /** Teleport the simulated partner to roughly [meters] away from [from]. */
    fun placePartnerNear(from: GeoPoint, meters: Double) {
        val c = couple.value ?: return
        val bearing = Random.nextDouble(0.0, 360.0)
        set(
            c.copy(
                partner = c.partner?.copy(
                    lastLocation = LocationSample(
                        point = offset(from, bearing, meters),
                        accuracyM = 8f,
                        timestampMs = System.currentTimeMillis(),
                    ),
                ),
            )
        )
    }

    /** Seed the partner near [from], then let them wander like a person walking. */
    fun seedPartnerNear(from: GeoPoint, meters: Double = 4200.0) {
        placePartnerNear(from, meters)
        startDrift()
    }

    private fun simulatedPartner(): MemberProfile = MemberProfile(
        uid = "mock-partner",
        name = partnerName,
        colorIndex = 5,
        lastLocation = LocationSample(
            point = GeoPoint(12.9716, 77.5946), // re-seeded near the user's first fix
            accuracyM = 8f,
            timestampMs = System.currentTimeMillis(),
        ),
    )

    private fun set(state: CoupleState?) {
        couple.value = state
        persist(state)
    }

    private fun persist(state: CoupleState?) {
        val p = prefs ?: return
        if (state == null) {
            p.edit().remove("state").apply()
            return
        }
        runCatching { p.edit().putString("state", toJson(state).toString()).apply() }
    }

    private fun restore() {
        val raw = prefs?.getString("state", null) ?: return
        runCatching {
            val state = fromJson(JSONObject(raw))
            couple.value = state
            if (state != null) startDrift()
        }
    }

    /** Gentle random walk, with heading wobble — feels like a person strolling. */
    private fun startDrift() {
        if (driftJobActive || !enabled) return
        driftJobActive = true
        scope.launch {
            var heading = Random.nextDouble(0.0, 360.0)
            while (isActive) {
                delay(12_000)
                if (!enabled) continue
                val c = couple.value ?: continue
                val partner = c.partner?.lastLocation ?: continue
                heading += Random.nextDouble(-35.0, 35.0)
                val moved = LocationSample(
                    point = offset(partner.point, heading, partnerSpeedMs * 12.0),
                    accuracyM = 8f,
                    timestampMs = System.currentTimeMillis(),
                )
                couple.value = c.copy(partner = c.partner?.copy(lastLocation = moved))
            }
        }
    }

    // ---- persistence --------------------------------------------------------

    private fun toJson(s: CoupleState) = JSONObject().apply {
        put("coupleId", s.coupleId)
        put("code", s.code)
        put("radiusM", s.radiusM)
        put("me", memberJson(s.me))
        s.partner?.let { put("partner", memberJson(it)) }
    }

    private fun memberJson(m: MemberProfile) = JSONObject().apply {
        put("uid", m.uid)
        put("name", m.name)
        put("colorIndex", m.colorIndex)
        put("sharingOn", m.sharingOn)
        m.lastLocation?.let {
            put("loc", JSONObject().apply {
                put("lat", it.point.lat)
                put("lng", it.point.lng)
                put("acc", it.accuracyM.toDouble())
                put("ts", it.timestampMs)
            })
        }
    }

    private fun fromJson(j: JSONObject): CoupleState? = runCatching {
        CoupleState(
            coupleId = j.getString("coupleId"),
            code = j.getString("code"),
            radiusM = j.getInt("radiusM"),
            me = memberFrom(j.getJSONObject("me")),
            partner = if (j.has("partner")) memberFrom(j.getJSONObject("partner")) else null,
        )
    }.getOrNull()

    private fun memberFrom(m: JSONObject) = MemberProfile(
        uid = m.getString("uid"),
        name = m.getString("name"),
        colorIndex = m.getInt("colorIndex"),
        sharingOn = m.optBoolean("sharingOn", true),
        lastLocation = if (m.has("loc")) {
            val loc = m.getJSONObject("loc")
            LocationSample(
                point = GeoPoint(loc.getDouble("lat"), loc.getDouble("lng")),
                accuracyM = loc.getDouble("acc").toFloat(),
                timestampMs = loc.getLong("ts"),
            )
        } else null,
    )

    companion object {
        private val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        fun generateCode(): String = (1..6).map { ALPHABET[Random.nextInt(ALPHABET.length)] }
            .joinToString("")

        /** Move [meters] along [bearingDeg] from [p], on a spherical earth. */
        fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
            val br = Math.toRadians(bearingDeg)
            val lat1 = Math.toRadians(p.lat)
            val lng1 = Math.toRadians(p.lng)
            val dr = meters / 6_371_008.8
            val lat2 = kotlin.math.asin(
                kotlin.math.sin(lat1) * kotlin.math.cos(dr) +
                    kotlin.math.cos(lat1) * kotlin.math.sin(dr) * kotlin.math.cos(br)
            )
            val lng2 = lng1 + kotlin.math.atan2(
                kotlin.math.sin(br) * kotlin.math.sin(dr) * kotlin.math.cos(lat1),
                kotlin.math.cos(dr) - kotlin.math.sin(lat1) * kotlin.math.sin(lat2),
            )
            return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2))
        }
    }
}
