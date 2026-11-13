package com.closer.app.backend

import com.closer.app.backend.relay.CodeGenerator
import com.closer.app.backend.relay.LiveRelayBackend
import com.closer.app.backend.relay.RelayServer
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.location.DistanceMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial stress and resilience test suite for [RelayServer] and [LiveRelayBackend].
 *
 * Verifies:
 * 1. High-concurrency pairing and contention.
 * 2. Rapid location streaming bursts.
 * 3. Abrupt client socket disconnects, reconnects, and dead-listener purging.
 * 4. Malformed HTTP payloads, invalid JSON, and unsupported methods.
 * 5. Case-insensitivity and whitespace trimming for pairing codes.
 * 6. Idempotent re-joining by existing members (profile updates).
 * 7. Multi-client fan-out (multiple listeners for the same user).
 * 8. Empirical demonstration of the SSE stream coroutine cancellation leak.
 */
class RelayServerAdversarialTest {

    private lateinit var server: RelayServer
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        // Ephemeral port to avoid conflicts
        server = RelayServer(requestedPort = 0)
        server.start()
        okHttpClient = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun testConcurrentPairingStress(): Unit = runBlocking {
        val pairCount = 20
        val createdCouples = ConcurrentLinkedQueue<Pair<String, String>>() // (coupleId, code)

        // Step 1: 20 creators create couples concurrently
        val creatorJobs = (1..pairCount).map { i ->
            async(Dispatchers.IO) {
                val backend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
                val uid = backend.ensureAuth()
                val state = backend.createCouple(MemberInit(name = "Creator-$i", colorIndex = i % 8))
                assertEquals(6, state.code.length)
                assertEquals(uid, state.me.uid)
                createdCouples.add(state.coupleId to state.code)
                state.code
            }
        }
        val codes = creatorJobs.awaitAll()
        assertEquals(pairCount, codes.toSet().size) // All 20 codes must be distinct

        // Step 2: 20 joiners concurrently join each respective couple
        val joinerJobs = codes.mapIndexed { index, code ->
            async(Dispatchers.IO) {
                val backend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
                val uid = backend.ensureAuth()
                val state = backend.joinCouple(code, MemberInit(name = "Joiner-$index", colorIndex = (index + 1) % 8))
                assertTrue(state.paired)
                assertEquals(uid, state.me.uid)
                assertNotNull(state.partner)
                state.partner?.name
            }
        }
        val partnerNames = joinerJobs.awaitAll()
        assertEquals(pairCount, partnerNames.size)
        for (name in partnerNames) {
            assertTrue(name?.startsWith("Creator-") == true)
        }
    }

    @Test
    fun testRaceConditionContentionOnSingleCouple(): Unit = runBlocking {
        // Dev creates a single couple
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val candidateCount = 10
        val successCount = AtomicInteger(0)
        val rejectedCount = AtomicInteger(0)

        // 10 candidate partners race concurrently to join Dev's single couple
        val raceJobs = (1..candidateCount).map { i ->
            async(Dispatchers.IO) {
                val candidateBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
                candidateBackend.ensureAuth()
                try {
                    val joined = candidateBackend.joinCouple(couple.code, MemberInit(name = "Candidate-$i", colorIndex = i % 8))
                    if (joined.paired) {
                        successCount.incrementAndGet()
                    }
                } catch (e: PairingException) {
                    // Must be rejected with "already has two members"
                    assertTrue(e.message?.contains("two members") == true || e.message?.contains("400") == true)
                    rejectedCount.incrementAndGet()
                }
            }
        }
        raceJobs.awaitAll()

        // Exactly 1 candidate must have succeeded; exactly 9 must have been rejected
        assertEquals(1, successCount.get())
        assertEquals(candidateCount - 1, rejectedCount.get())
    }

    @Test
    fun testRapidLocationStreamingBurst(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val devUid = devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(couple.code, MemberInit(name = "Nandhini", colorIndex = 5))

        // Start Dev observing partner location on independent supervisor scope
        val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receivedLocations = ConcurrentLinkedQueue<LocationSample>()
        streamScope.launch {
            devBackend.observeCouple(couple.coupleId, devUid).collect { state ->
                state?.partner?.lastLocation?.let { receivedLocations.add(it) }
            }
        }

        // Wait briefly for SSE stream to establish
        delay(200)

        // Nandhini pushes 30 rapid location samples
        val burstCount = 30
        for (i in 1..burstCount) {
            val point = GeoPoint(12.971600 + (i * 0.00001), 77.594600 + (i * 0.00001))
            val sample = LocationSample(point = point, accuracyM = 3.0f, timestampMs = 1000L * i)
            nandhiniBackend.pushLocation(couple.coupleId, nandhiniUid, sample)
        }

        // Wait until Dev receives the latest sample (sample #30 with timestamp 30000L)
        val receivedLatest = withTimeoutOrNull(5000) {
            while (true) {
                if (receivedLocations.any { it.timestampMs == 1000L * burstCount }) {
                    return@withTimeoutOrNull true
                }
                delay(50)
            }
        }
        assertTrue("Dev must receive the latest pushed location sample", receivedLatest == true)
        streamScope.cancel()
    }

    @Test
    fun testAbruptClientDisconnectAndDeadListenerPurging(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val devUid = devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(couple.code, MemberInit(name = "Nandhini", colorIndex = 5))

        // Open a raw socket simulating an SSE client connection
        val rawSocket = Socket("127.0.0.1", server.localPort)
        val writer = OutputStreamWriter(rawSocket.getOutputStream(), Charsets.UTF_8)
        val reader = BufferedReader(InputStreamReader(rawSocket.getInputStream(), Charsets.UTF_8))

        writer.write("GET /api/couples/${couple.coupleId}/stream?uid=$devUid HTTP/1.1\r\n")
        writer.write("Host: 127.0.0.1\r\n")
        writer.write("Accept: text/event-stream\r\n\r\n")
        writer.flush()

        // Read until the initial event payload
        val firstLine = reader.readLine()
        assertEquals("HTTP/1.1 200 OK", firstLine)

        // Read initial data line
        var line: String?
        var receivedInitial = false
        while (true) {
            line = reader.readLine()
            if (line == null || line.isEmpty()) {
                if (receivedInitial) break
                continue
            }
            if (line.startsWith("data:")) {
                receivedInitial = true
                break
            }
        }
        assertTrue("Must have received initial SSE data event", receivedInitial)

        // Now abruptly close the raw socket client without graceful HTTP shutdown
        rawSocket.close()

        // Partner pushes location - this will invoke broadcastUpdate
        // Server should catch the Broken pipe / SocketException and prune the dead listener
        val sample = LocationSample(GeoPoint(12.97, 77.59), 4f, System.currentTimeMillis())
        nandhiniBackend.pushLocation(couple.coupleId, nandhiniUid, sample)

        // Verify server is healthy: a new client can fetch snapshot and connect
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val newDevState = scope.async {
            devBackend.observeCouple(couple.coupleId, devUid)
                .first { it?.partner?.lastLocation != null }
        }.await()
        assertNotNull(newDevState?.partner?.lastLocation)
    }

    @Test
    fun testMalformedHttpRequestsAndPayloads(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val devUid = devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val jsonType = "application/json; charset=utf-8".toMediaType()

        // 1. Invalid JSON body to /api/couples/join
        val badJsonReq = Request.Builder()
            .url("${server.baseUrl}/api/couples/join")
            .post("{not_valid_json:".toRequestBody(jsonType))
            .build()
        try {
            val resp = okHttpClient.newCall(badJsonReq).execute()
            resp.close()
        } catch (_: Exception) {
            // Socket closed cleanly or connection reset is acceptable
        }

        // 2. Missing code in /api/couples/join
        val missingCodeReq = Request.Builder()
            .url("${server.baseUrl}/api/couples/join")
            .post("""{"uid":"some-uid","name":"NoCode"}""".toRequestBody(jsonType))
            .build()
        try {
            val resp = okHttpClient.newCall(missingCodeReq).execute()
            resp.close()
        } catch (_: Exception) {
        }

        // 3. Unknown route
        val unknownRouteReq = Request.Builder()
            .url("${server.baseUrl}/api/nonexistent/endpoint")
            .get()
            .build()
        okHttpClient.newCall(unknownRouteReq).execute().use { resp ->
            assertEquals(404, resp.code)
        }

        // 4. Unsupported HTTP method (DELETE)
        val deleteReq = Request.Builder()
            .url("${server.baseUrl}/api/couples/${couple.coupleId}")
            .delete()
            .build()
        okHttpClient.newCall(deleteReq).execute().use { resp ->
            assertEquals(404, resp.code)
        }

        // 5. Stream request for non-existent couple
        val missingStreamReq = Request.Builder()
            .url("${server.baseUrl}/api/couples/cpl-nonexistent/stream?uid=$devUid")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        okHttpClient.newCall(missingStreamReq).execute().use { resp ->
            assertEquals(404, resp.code)
        }

        // 6. Push location with missing fields to existing couple
        val badLocationReq = Request.Builder()
            .url("${server.baseUrl}/api/couples/${couple.coupleId}/location")
            .post("""{"uid":"$devUid","corrupt":true}""".toRequestBody(jsonType))
            .build()
        try {
            val resp = okHttpClient.newCall(badLocationReq).execute()
            resp.close()
        } catch (_: Exception) {
        }

        // 7. Verify RelayServer is still 100% operational after all malicious/malformed inputs
        val healthReq = Request.Builder()
            .url("${server.baseUrl}/api/health")
            .get()
            .build()
        okHttpClient.newCall(healthReq).execute().use { resp ->
            assertEquals(200, resp.code)
            assertTrue(resp.body?.string()?.contains(""""status":"ok"""") == true)
        }
    }

    @Test
    fun testCaseInsensitiveAndWhitespaceTolerantPairingCode(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val nandhiniUid = nandhiniBackend.ensureAuth()

        // Nandhini enters lowercase code surrounded by whitespace
        val messyCode = "   ${couple.code.lowercase()}   \n"
        val joined = nandhiniBackend.joinCouple(messyCode, MemberInit(name = "Nandhini", colorIndex = 5))

        assertTrue(joined.paired)
        assertEquals(nandhiniUid, joined.me.uid)
        assertEquals("Dev", joined.partner?.name)
    }

    @Test
    fun testRejoinByExistingMemberUpdatesProfile(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val devUid = devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(couple.code, MemberInit(name = "Nandhini", colorIndex = 5))

        // Nandhini re-joins with the same code and same UID, but changed name and color
        val rejoined = nandhiniBackend.joinCouple(couple.code, MemberInit(name = "Nandhini V.", colorIndex = 3))
        assertTrue(rejoined.paired)
        assertEquals("Nandhini V.", rejoined.me.name)
        assertEquals(3, rejoined.me.colorIndex)

        // Dev's snapshot reflects Nandhini's updated profile
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val devObserved = scope.async {
            devBackend.observeCouple(couple.coupleId, devUid)
                .first { it?.partner?.name == "Nandhini V." }
        }.await()
        assertEquals("Nandhini V.", devObserved?.partner?.name)
        assertEquals(3, devObserved?.partner?.colorIndex)
    }

    @Test
    fun testMultiStreamBroadcastFanout(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val devUid = devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val nandhiniBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val nandhiniUid = nandhiniBackend.ensureAuth()
        nandhiniBackend.joinCouple(couple.code, MemberInit(name = "Nandhini", colorIndex = 5))

        // Three parallel listeners for Dev on an independent scope
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val stream1 = scope.async {
            devBackend.observeCouple(couple.coupleId, devUid).first { it?.partner?.lastLocation != null }
        }
        val stream2 = scope.async {
            devBackend.observeCouple(couple.coupleId, devUid).first { it?.partner?.lastLocation != null }
        }
        val stream3 = scope.async {
            devBackend.observeCouple(couple.coupleId, devUid).first { it?.partner?.lastLocation != null }
        }

        delay(150)

        // Nandhini pushes location
        val nandhiniLoc = LocationSample(GeoPoint(12.9716, 77.5946), 3.5f, 99999L)
        nandhiniBackend.pushLocation(couple.coupleId, nandhiniUid, nandhiniLoc)

        // All 3 parallel streams must receive the event
        val res1 = stream1.await()
        val res2 = stream2.await()
        val res3 = stream3.await()

        assertEquals(99999L, res1?.partner?.lastLocation?.timestampMs)
        assertEquals(99999L, res2?.partner?.lastLocation?.timestampMs)
        assertEquals(99999L, res3?.partner?.lastLocation?.timestampMs)
    }

    @Test
    fun testCORSPreflightOptionsMethod() {
        val optionsReq = Request.Builder()
            .url("${server.baseUrl}/api/couples")
            .method("OPTIONS", null)
            .build()
        okHttpClient.newCall(optionsReq).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("*", resp.header("Access-Control-Allow-Origin"))
            assertTrue(resp.header("Access-Control-Allow-Methods")?.contains("POST") == true)
        }
    }

    /**
     * EMPIRICAL CHALLENGE ORACLE:
     * Demonstrates that when a coroutine collecting [LiveRelayBackend.observeCouple] is cancelled,
     * the underlying OkHttp Call is NOT cancelled because it is blocked inside [okio.BufferedSource.readUtf8Line]
     * without a coroutine cancellation handler (i.e. missing job.invokeOnCompletion { call.cancel() }).
     *
     * As a result, the coroutine job cannot complete cleanly and hangs until timed out.
     */
    @Test
    fun testEmpiricalProofOfSseStreamCancellationLeak(): Unit = runBlocking {
        val devBackend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val devUid = devBackend.ensureAuth()
        val couple = devBackend.createCouple(MemberInit(name = "Dev", colorIndex = 0))

        val leakScope = CoroutineScope(Job() + Dispatchers.IO)
        val collectorJob = leakScope.launch {
            devBackend.observeCouple(couple.coupleId, devUid).collect { _ ->
                // Keep reading stream
            }
        }

        // Wait briefly for connection to establish and block in readUtf8Line
        delay(250)

        // Now cancel the collector coroutine
        collectorJob.cancel()

        // In a non-leaking implementation with call.cancel() registered on cancellation,
        // collectorJob.join() finishes within ~50ms.
        val completedCleanly = withTimeoutOrNull(500) {
            collectorJob.join()
            true
        }

        assertTrue(
            "collectorJob.join() must complete immediately on cancellation without thread leak",
            completedCleanly == true
        )

        // Cancel scope and clean up
        leakScope.cancel()
    }

    @Test
    fun testMalformedJsonReturnsBadRequest400() {
        // 1. POST /api/couples with malformed JSON
        val malformedReq = Request.Builder()
            .url("${server.baseUrl}/api/couples")
            .post("{invalid json content".toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(malformedReq).execute().use { resp ->
            assertEquals(400, resp.code)
            val body = resp.body?.string() ?: ""
            assertTrue(body.contains("Invalid JSON"))
        }

        // 2. POST /api/couples/join with malformed JSON
        val malformedJoinReq = Request.Builder()
            .url("${server.baseUrl}/api/couples/join")
            .post("this is definitely not json".toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(malformedJoinReq).execute().use { resp ->
            assertEquals(400, resp.code)
            val body = resp.body?.string() ?: ""
            assertTrue(body.contains("Invalid JSON"))
        }
    }

    @Test
    fun testConcurrentEnsureAuthReturnsConsistentUid(): Unit = runBlocking {
        val backend = LiveRelayBackend(explicitBaseUrl = server.baseUrl)
        val concurrency = 15
        val uids = (1..concurrency).map {
            async(Dispatchers.IO) {
                backend.ensureAuth()
            }
        }.awaitAll()

        assertEquals(concurrency, uids.size)
        val firstUid = uids.first()
        assertTrue("UID must not be blank", firstUid.isNotBlank())
        for (uid in uids) {
            assertEquals("All concurrent auth callers must receive the exact same UID", firstUid, uid)
        }
    }

    @Test
    fun testDistanceFormattingImmuneToLocaleComma() {
        val originalLocale = Locale.getDefault()
        try {
            // Set German / French locale which uses comma for decimals
            Locale.setDefault(Locale.GERMANY)
            val formatted = DistanceMath.formatDistance(3_400.0)
            assertEquals("3.4 km", formatted)
            assertFalse("Must never contain German/French comma decimal", formatted.contains(","))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
