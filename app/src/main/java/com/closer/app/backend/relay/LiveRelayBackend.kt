package com.closer.app.backend.relay

import android.content.Context
import com.closer.app.backend.Backend
import com.closer.app.backend.MemberInit
import com.closer.app.backend.PairingException
import com.closer.app.data.CoupleState
import com.closer.app.data.LocationSample
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
        val body = JSONObject().apply {
            put("code", code.trim().uppercase())
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
        // Step 1: Initial state fetch via REST snapshot
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
    }
}
