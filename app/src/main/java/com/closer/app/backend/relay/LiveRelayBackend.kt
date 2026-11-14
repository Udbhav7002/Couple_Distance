package com.closer.app.backend.relay

import android.content.Context
import com.closer.app.backend.Backend
import com.closer.app.backend.MemberInit
import com.closer.app.backend.PairingException
import com.closer.app.data.CoupleState
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.data.MemberProfile
import com.closer.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Production-ready network relay implementation of [Backend] using OkHttp and SSE.
 * Supports both standalone in-process RelayServer (for tests) and global public HTTPS relay (ntfy.sh).
 */
class LiveRelayBackend(
    private val context: Context? = null,
    var explicitBaseUrl: String? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for SSE streaming
        .build(),
) : Backend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authMutex = Mutex()
    @Volatile private var dynamicBaseUrl: String? = null
    @Volatile private var cachedUid: String? = null

    val baseUrl: String
        get() {
            val explicit = explicitBaseUrl
            if (!explicit.isNullOrBlank()) return explicit.trimEnd('/')
            val dyn = dynamicBaseUrl
            if (!dyn.isNullOrBlank()) return dyn.trimEnd('/')
            return SettingsStore.DEFAULT_RELAY_URL.trimEnd('/')
        }

    private val isNtfy: Boolean
        get() = baseUrl.contains("ntfy.sh") || (!baseUrl.contains("127.0.0.1") && !baseUrl.contains("localhost"))

    private fun ntfyTopic(code: String): String = "closer_v1_" + code.trim().uppercase()

    private fun postNtfy(topic: String, payload: JSONObject) {
        val url = "$baseUrl/$topic"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Network error: ${resp.code} on $topic")
            }
        }
    }

    private fun pollNtfyMessages(topic: String): List<JSONObject> {
        val url = "$baseUrl/$topic/json?poll=1&since=all"
        val request = Request.Builder().url(url).get().build()
        val list = mutableListOf<JSONObject>()
        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val source = resp.body?.source() ?: return emptyList()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val ntfyObj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (ntfyObj.optString("event") == "message") {
                    val raw = ntfyObj.optString("message")
                    runCatching { JSONObject(raw) }.getOrNull()?.let { list.add(it) }
                }
            }
        }
        return list
    }

    init {
        if (context != null) {
            val settings = SettingsStore(context)
            scope.launch {
                settings.relayUrl.collect { url ->
                    dynamicBaseUrl = url
                }
            }
        }
    }

    override suspend fun ensureAuth(): String = authMutex.withLock {
        withContext(Dispatchers.IO) {
            cachedUid?.let { return@withContext it }

            val prefs = context?.applicationContext
                ?.getSharedPreferences("closer_relay_auth", Context.MODE_PRIVATE)
            val existing = prefs?.getString("uid", null)
            if (!existing.isNullOrEmpty()) {
                cachedUid = existing
                return@withContext existing
            }

            if (isNtfy) {
                val generated = "user-" + UUID.randomUUID().toString().take(12)
                prefs?.edit()?.putString("uid", generated)?.apply()
                cachedUid = generated
                return@withContext generated
            }

            val generated = try {
                val request = Request.Builder()
                    .url("$baseUrl/api/auth")
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val j = JSONObject(resp.body?.string() ?: "{}")
                        j.optString("uid").ifEmpty { "user-" + UUID.randomUUID().toString().take(12) }
                    } else {
                        "user-" + UUID.randomUUID().toString().take(12)
                    }
                }
            } catch (_: Exception) {
                "user-" + UUID.randomUUID().toString().take(12)
            }

            prefs?.edit()?.putString("uid", generated)?.apply()
            cachedUid = generated
            generated
        }
    }

    override suspend fun createCouple(me: MemberInit): CoupleState = withContext(Dispatchers.IO) {
        val uid = ensureAuth()

        if (isNtfy) {
            val code = CodeGenerator.generate()
            val coupleId = "c_$code"
            val topic = ntfyTopic(code)
            val body = JSONObject().apply {
                put("action", "create")
                put("coupleId", coupleId)
                put("code", code)
                put("radiusM", 200)
                put("uid", uid)
                put("name", me.name)
                put("colorIndex", me.colorIndex)
                put("createdAt", System.currentTimeMillis())
            }
            postNtfy(topic, body)
            return@withContext CoupleState(
                coupleId = coupleId,
                code = code,
                radiusM = 200,
                me = MemberProfile(uid = uid, name = me.name, colorIndex = me.colorIndex),
                partner = null
            )
        }

        val body = JSONObject().apply {
            put("uid", uid)
            put("name", me.name)
            put("colorIndex", me.colorIndex)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/couples")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val respStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw PairingException("Failed to create couple: ${resp.code} $respStr")
            }
            RelayJson.coupleStateFromJson(JSONObject(respStr))
        }
    }

    override suspend fun joinCouple(code: String, me: MemberInit): CoupleState = withContext(Dispatchers.IO) {
        val uid = ensureAuth()
        val cleanCode = code.trim().uppercase()

        if (isNtfy) {
            val topic = ntfyTopic(cleanCode)
            var messages = emptyList<JSONObject>()
            for (attempt in 1..6) {
                messages = pollNtfyMessages(topic)
                if (messages.any { it.optString("action") == "create" }) break
                delay(400)
            }
            val createMsg = messages.firstOrNull { it.optString("action") == "create" }
                ?: throw PairingException("No couple found for code $cleanCode")

            val createdAt = createMsg.optLong("createdAt", 0L)
            if (createdAt > 0 && System.currentTimeMillis() - createdAt > 24 * 3600 * 1000L) {
                throw PairingException("This pairing code has expired. Ask your partner to create a new couple.")
            }

            val creatorUid = createMsg.getString("uid")
            val creatorName = createMsg.getString("name")
            val creatorColor = createMsg.getInt("colorIndex")
            val radiusM = createMsg.optInt("radiusM", 200)

            val joinMsgs = messages.filter { it.optString("action") == "join" }
            val otherJoiner = joinMsgs.firstOrNull { it.optString("uid") != creatorUid && it.optString("uid") != uid }
            if (otherJoiner != null) {
                throw PairingException("That couple already has two members")
            }

            val coupleId = "c_$cleanCode"
            val joinBody = JSONObject().apply {
                put("action", "join")
                put("coupleId", coupleId)
                put("code", cleanCode)
                put("uid", uid)
                put("name", me.name)
                put("colorIndex", me.colorIndex)
            }
            postNtfy(topic, joinBody)

            val partnerProfile = MemberProfile(uid = creatorUid, name = creatorName, colorIndex = creatorColor)
            return@withContext CoupleState(
                coupleId = coupleId,
                code = cleanCode,
                radiusM = radiusM,
                me = MemberProfile(uid = uid, name = me.name, colorIndex = me.colorIndex),
                partner = partnerProfile
            )
        }

        val body = JSONObject().apply {
            put("code", cleanCode)
            put("uid", uid)
            put("name", me.name)
            put("colorIndex", me.colorIndex)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/couples/join")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val respStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val err = runCatching { JSONObject(respStr).optString("error") }.getOrNull()
                throw PairingException(err ?: "Failed to join couple: ${resp.code}")
            }
            RelayJson.coupleStateFromJson(JSONObject(respStr))
        }
    }

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    override fun observeCouple(coupleId: String, myUid: String): Flow<CoupleState?> = flow {
        if (isNtfy) {
            val cleanCode = coupleId.removePrefix("c_")
            val topic = ntfyTopic(cleanCode)

            var codeStr = cleanCode
            var radius = 200
            var creatorProfile: MemberProfile? = null
            var joinerProfile: MemberProfile? = null

            fun buildState(): CoupleState? {
                val creator = creatorProfile ?: return null
                val me = if (myUid == creator.uid) creator else (joinerProfile ?: return null)
                val partner = if (myUid == creator.uid) joinerProfile else creator
                return CoupleState(
                    coupleId = coupleId,
                    code = codeStr,
                    radiusM = radius,
                    me = me,
                    partner = partner
                )
            }

            fun applyMessage(j: JSONObject) {
                when (j.optString("action")) {
                    "create" -> {
                        radius = j.optInt("radiusM", radius)
                        codeStr = j.optString("code", codeStr)
                        val u = j.optString("uid")
                        creatorProfile = (creatorProfile ?: MemberProfile(u, j.optString("name"), j.optInt("colorIndex"))).copy(
                            name = j.optString("name", creatorProfile?.name ?: ""),
                            colorIndex = j.optInt("colorIndex", creatorProfile?.colorIndex ?: 0)
                        )
                    }
                    "join" -> {
                        val u = j.optString("uid")
                        joinerProfile = (joinerProfile ?: MemberProfile(u, j.optString("name"), j.optInt("colorIndex"))).copy(
                            name = j.optString("name", joinerProfile?.name ?: ""),
                            colorIndex = j.optInt("colorIndex", joinerProfile?.colorIndex ?: 0)
                        )
                    }
                    "loc" -> {
                        val u = j.optString("uid")
                        val sample = RelayJson.locationSampleFromJson(j)
                        if (u == creatorProfile?.uid) {
                            creatorProfile = creatorProfile?.copy(lastLocation = sample)
                        } else {
                            joinerProfile = (joinerProfile ?: MemberProfile(u, "", 0)).copy(lastLocation = sample)
                        }
                    }
                    "sharing" -> {
                        val u = j.optString("uid")
                        val on = j.optBoolean("sharingOn", true)
                        if (u == creatorProfile?.uid) {
                            creatorProfile = creatorProfile?.copy(sharingOn = on)
                        } else {
                            joinerProfile = (joinerProfile ?: MemberProfile(u, "", 0)).copy(sharingOn = on)
                        }
                    }
                    "radius" -> {
                        radius = j.optInt("radiusM", radius)
                    }
                    "profile" -> {
                        val u = j.optString("uid")
                        val name = j.optString("name")
                        val color = j.optInt("colorIndex")
                        if (u == creatorProfile?.uid) {
                            creatorProfile = creatorProfile?.copy(name = name, colorIndex = color)
                        } else {
                            joinerProfile = (joinerProfile ?: MemberProfile(u, "", 0)).copy(name = name, colorIndex = color)
                        }
                    }
                }
            }

            runCatching {
                val past = pollNtfyMessages(topic)
                for (msg in past) applyMessage(msg)
            }
            buildState()?.let { emit(it) }

            while (currentCoroutineContext().isActive) {
                var activeCall: okhttp3.Call? = null
                try {
                    val url = "$baseUrl/$topic/sse?since=all"
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .build()
                    val call = okHttpClient.newCall(request)
                    activeCall = call
                    var activeResponse: okhttp3.Response? = null
                    val cancelHandle = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true) {
                        try { call.cancel() } catch (_: Throwable) {}
                        try { activeResponse?.close() } catch (_: Throwable) {}
                    }

                    try {
                        call.execute().use { resp ->
                            activeResponse = resp
                            if (!resp.isSuccessful) {
                                delay(1000)
                                return@use
                            }
                            val source = resp.body?.source() ?: return@use
                            while (currentCoroutineContext().isActive) {
                                val line = source.readUtf8Line() ?: break
                                if (line.startsWith("data:")) {
                                    val jsonStr = line.removePrefix("data:").trim()
                                    if (jsonStr.isNotEmpty()) {
                                        val ntfyObj = runCatching { JSONObject(jsonStr) }.getOrNull()
                                        if (ntfyObj != null && ntfyObj.optString("event") == "message") {
                                            val raw = ntfyObj.optString("message")
                                            val payloadObj = runCatching { JSONObject(raw) }.getOrNull()
                                            if (payloadObj != null) {
                                                applyMessage(payloadObj)
                                                buildState()?.let { emit(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        activeResponse = null
                        cancelHandle.dispose()
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (!currentCoroutineContext().isActive) break
                    delay(1000)
                } finally {
                    try { activeCall?.cancel() } catch (_: Throwable) {}
                }
            }
            return@flow
        }

        // Step 1: Initial state fetch via REST snapshot (for RelayServer)
        runCatching {
            fetchCoupleSnapshot(coupleId, myUid)
        }.getOrNull()?.let { emit(it) }

        // Step 2: Continuous SSE streaming loop with reconnection
        while (currentCoroutineContext().isActive) {
            var activeCall: okhttp3.Call? = null
            try {
                val url = "$baseUrl/api/couples/$coupleId/stream?uid=$myUid"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .build()

                val call = okHttpClient.newCall(request)
                activeCall = call
                var activeResponse: okhttp3.Response? = null
                val cancelHandle = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true) {
                    try { call.cancel() } catch (_: Throwable) {}
                    try { activeResponse?.close() } catch (_: Throwable) {}
                }

                try {
                    call.execute().use { resp ->
                        activeResponse = resp
                        if (!resp.isSuccessful) {
                            delay(1000)
                            return@use
                        }
                        val source = resp.body?.source() ?: return@use
                        while (currentCoroutineContext().isActive) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data:")) {
                                val jsonStr = line.removePrefix("data:").trim()
                                if (jsonStr.isNotEmpty()) {
                                    val state = RelayJson.coupleStateFromJson(JSONObject(jsonStr))
                                    emit(state)
                                }
                            }
                        }
                    }
                } finally {
                    activeResponse = null
                    cancelHandle.dispose()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!currentCoroutineContext().isActive) break
                delay(1000)
            } finally {
                try { activeCall?.cancel() } catch (_: Throwable) {}
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchCoupleSnapshot(coupleId: String, myUid: String): CoupleState? {
        val request = Request.Builder()
            .url("$baseUrl/api/couples/$coupleId?uid=$myUid")
            .get()
            .build()
        return okHttpClient.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return null
                RelayJson.coupleStateFromJson(JSONObject(body))
            } else null
        }
    }

    override suspend fun pushLocation(coupleId: String, uid: String, sample: LocationSample) = withContext(Dispatchers.IO) {
        if (isNtfy) {
            val cleanCode = coupleId.removePrefix("c_")
            val topic = ntfyTopic(cleanCode)
            val body = RelayJson.locationSampleToJson(sample).apply {
                put("action", "loc")
                put("uid", uid)
            }
            postNtfy(topic, body)
            return@withContext
        }

        val body = RelayJson.locationSampleToJson(sample).apply {
            put("uid", uid)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/couples/$coupleId/location")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Push location failed: ${resp.code}")
            }
        }
    }

    override suspend fun setSharing(coupleId: String, uid: String, on: Boolean) = withContext(Dispatchers.IO) {
        if (isNtfy) {
            val cleanCode = coupleId.removePrefix("c_")
            val topic = ntfyTopic(cleanCode)
            val body = JSONObject().apply {
                put("action", "sharing")
                put("uid", uid)
                put("sharingOn", on)
            }
            postNtfy(topic, body)
            return@withContext
        }

        val body = JSONObject().apply {
            put("uid", uid)
            put("sharingOn", on)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/couples/$coupleId/sharing")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Set sharing failed: ${resp.code}")
            }
        }
    }

    override suspend fun setRadius(coupleId: String, radiusM: Int) = withContext(Dispatchers.IO) {
        if (isNtfy) {
            val cleanCode = coupleId.removePrefix("c_")
            val topic = ntfyTopic(cleanCode)
            val body = JSONObject().apply {
                put("action", "radius")
                put("radiusM", radiusM)
            }
            postNtfy(topic, body)
            return@withContext
        }

        val body = JSONObject().apply {
            put("radiusM", radiusM)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/couples/$coupleId/radius")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Set radius failed: ${resp.code}")
            }
        }
    }

    override suspend fun updateMe(coupleId: String, uid: String, name: String, colorIndex: Int) = withContext(Dispatchers.IO) {
        if (isNtfy) {
            val cleanCode = coupleId.removePrefix("c_")
            val topic = ntfyTopic(cleanCode)
            val body = JSONObject().apply {
                put("action", "profile")
                put("uid", uid)
                put("name", name)
                put("colorIndex", colorIndex)
            }
            postNtfy(topic, body)
            return@withContext
        }

        val body = JSONObject().apply {
            put("uid", uid)
            put("name", name)
            put("colorIndex", colorIndex)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/couples/$coupleId/profile")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Update profile failed: ${resp.code}")
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()
    }
}
