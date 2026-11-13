package com.closer.app.backend.relay

import com.closer.app.data.CoupleState
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.data.MemberProfile
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Lightweight, robust, standalone HTTP + SSE Relay Server.
 * Supports pairing two devices over the network and real-time state broadcast.
 */
class RelayServer(val requestedPort: Int = 8765) : Closeable {

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    private val executor = Executors.newCachedThreadPool()
    private var acceptThread: Thread? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: requestedPort

    val baseUrl: String
        get() = "http://127.0.0.1:$localPort"

    // In-memory state
    private val couplesById = ConcurrentHashMap<String, ServerCouple>()
    private val coupleIdByCode = ConcurrentHashMap<String, String>()

    data class ServerMember(
        val uid: String,
        var name: String,
        var colorIndex: Int,
        var sharingOn: Boolean = true,
        var lastLocation: LocationSample? = null,
        var batteryPct: Int? = null,
    )

    data class ServerCouple(
        val coupleId: String,
        val code: String,
        var radiusM: Int = 200,
        val creatorUid: String,
        val members: MutableMap<String, ServerMember> = ConcurrentHashMap(),
        val listeners: MutableList<StreamListener> = CopyOnWriteArrayList(),
    )

    data class StreamListener(
        val uid: String,
        val output: OutputStream,
        val socket: Socket,
    )

    @Synchronized
    fun start() {
        if (running) return
        val ss = ServerSocket(requestedPort)
        serverSocket = ss
        running = true

        acceptThread = Thread({
            while (running && !ss.isClosed) {
                try {
                    val clientSocket = ss.accept()
                    clientSocket.tcpNoDelay = true
                    executor.execute { handleConnection(clientSocket) }
                } catch (_: SocketException) {
                    if (!running) break
                } catch (_: Exception) {
                }
            }
        }, "RelayServer-Accept").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}

        for (couple in couplesById.values) {
            for (listener in couple.listeners) {
                try {
                    listener.socket.close()
                } catch (_: Exception) {}
            }
            couple.listeners.clear()
        }

        executor.shutdownNow()
        acceptThread?.interrupt()
        serverSocket = null
    }

    override fun close() = stop()

    private fun handleConnection(socket: Socket) {
        var keepConnectionOpen = false
        var outputStream: BufferedOutputStream? = null
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            outputStream = output

            val requestLine = readLineFromStream(input) ?: run {
                socket.close()
                return
            }

            val headerLines = mutableListOf<String>()
            while (true) {
                val line = readLineFromStream(input) ?: break
                if (line.isEmpty()) break
                headerLines.add(line)
            }

            val headers = mutableMapOf<String, String>()
            for (h in headerLines) {
                val idx = h.indexOf(':')
                if (idx > 0) {
                    headers[h.substring(0, idx).trim().lowercase()] = h.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBytes = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val count = input.read(buf, readTotal, contentLength - readTotal)
                    if (count == -1) break
                    readTotal += count
                }
                buf
            } else ByteArray(0)
            val bodyString = String(bodyBytes, Charsets.UTF_8)

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val uriParts = parts[1].split("?", limit = 2)
            val path = uriParts[0]
            val queryParams = parseQueryParams(if (uriParts.size > 1) uriParts[1] else "")

            if (method == "OPTIONS") {
                sendResponse(output, 200, "OK", body = "")
                return
            }

            // Route handling
            when {
                // Health
                path == "/api/health" && method == "GET" -> {
                    sendResponse(output, 200, "OK", body = """{"status":"ok"}""")
                }

                // Auth
                (path == "/api/auth" && (method == "POST" || method == "GET")) -> {
                    val uid = "user-" + UUID.randomUUID().toString().take(12)
                    sendResponse(output, 200, "OK", body = JSONObject().put("uid", uid).toString())
                }

                // Create couple
                path == "/api/couples" && method == "POST" -> {
                    val bodyJson = try {
                        if (bodyString.isNotBlank()) JSONObject(bodyString) else JSONObject()
                    } catch (_: JSONException) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }
                    val uid = bodyJson.optString("uid").ifEmpty { "user-" + UUID.randomUUID().toString().take(12) }
                    val name = bodyJson.optString("name", "User")
                    val colorIndex = bodyJson.optInt("colorIndex", 0)

                    val coupleId = "cpl-" + UUID.randomUUID().toString().take(12)
                    var code = CodeGenerator.generate().trim().uppercase()
                    while (coupleIdByCode.containsKey(code)) {
                        code = CodeGenerator.generate().trim().uppercase()
                    }

                    val member = ServerMember(uid = uid, name = name, colorIndex = colorIndex)
                    val couple = ServerCouple(
                        coupleId = coupleId,
                        code = code,
                        radiusM = 200,
                        creatorUid = uid,
                    )
                    couple.members[uid] = member
                    couplesById[coupleId] = couple
                    coupleIdByCode[code] = coupleId

                    val resp = toCoupleStateJson(couple, uid)
                    sendResponse(output, 201, "Created", body = resp.toString())
                }

                // Join couple
                path == "/api/couples/join" && method == "POST" -> {
                    val bodyJson = try {
                        JSONObject(bodyString)
                    } catch (_: JSONException) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }
                    val code = bodyJson.optString("code").trim().uppercase()
                    if (code.isEmpty()) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Code not found. Please check and try again."}""")
                        return
                    }
                    val uid = bodyJson.optString("uid").ifEmpty { "user-" + UUID.randomUUID().toString().take(12) }
                    val name = bodyJson.optString("name", "Partner")
                    val colorIndex = bodyJson.optInt("colorIndex", 5)

                    val coupleId = coupleIdByCode[code]
                    if (coupleId == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Code not found. Please check and try again."}""")
                        return
                    }

                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found."}""")
                        return
                    }

                    synchronized(couple) {
                        if (!couple.members.containsKey(uid) && couple.members.size >= 2) {
                            sendResponse(output, 400, "Bad Request", body = """{"error":"This couple already has two members."}""")
                            return
                        }
                        val existing = couple.members[uid]
                        if (existing != null) {
                            existing.name = name
                            existing.colorIndex = colorIndex
                        } else {
                            couple.members[uid] = ServerMember(uid = uid, name = name, colorIndex = colorIndex)
                        }
                    }

                    broadcastUpdate(couple)
                    val resp = toCoupleStateJson(couple, uid)
                    sendResponse(output, 200, "OK", body = resp.toString())
                }

                // Stream couple events (SSE)
                path.matches(Regex("""^/api/couples/[^/]+/stream$""")) && method == "GET" -> {
                    val coupleId = path.substringAfter("/api/couples/").substringBefore("/stream")
                    val uid = queryParams["uid"] ?: ""
                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found"}""")
                        return
                    }

                    // SSE connection response headers
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/event-stream; charset=utf-8\r\n")
                        append("Cache-Control: no-cache\r\n")
                        append("Connection: keep-alive\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("\r\n")
                    }
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.flush()

                    val listener = StreamListener(uid = uid, output = output, socket = socket)
                    synchronized(couple) {
                        couple.listeners.add(listener)
                        // Send initial state event within synchronized block so no concurrent joins are missed
                        val initialJson = toCoupleStateJson(couple, uid)
                        val initPayload = "data: $initialJson\n\n".toByteArray(Charsets.UTF_8)
                        output.write(initPayload)
                        output.flush()
                    }
                    keepConnectionOpen = true

                    try {
                        val buf = ByteArray(64)
                        while (running && !socket.isClosed) {
                            val r = input.read(buf)
                            if (r == -1) break
                        }
                    } catch (_: Exception) {
                    } finally {
                        couple.listeners.remove(listener)
                        try { socket.close() } catch (_: Exception) {}
                    }
                }

                // Get single couple state snapshot
                path.matches(Regex("""^/api/couples/[^/]+$""")) && method == "GET" -> {
                    val coupleId = path.substringAfter("/api/couples/")
                    val uid = queryParams["uid"] ?: ""
                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found"}""")
                        return
                    }
                    val resp = toCoupleStateJson(couple, uid)
                    sendResponse(output, 200, "OK", body = resp.toString())
                }

                // Push location
                path.matches(Regex("""^/api/couples/[^/]+/location$""")) && method == "POST" -> {
                    val coupleId = path.substringAfter("/api/couples/").substringBefore("/location")
                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found"}""")
                        return
                    }
                    val bodyJson = try {
                        JSONObject(bodyString)
                    } catch (_: JSONException) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }
                    val uid = bodyJson.optString("uid")
                    val sample = try {
                        RelayJson.locationSampleFromJson(bodyJson)
                    } catch (_: Exception) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }

                    synchronized(couple) {
                        val member = couple.members[uid]
                        if (member != null) {
                            member.lastLocation = sample
                            if (sample.batteryPct != null) {
                                member.batteryPct = sample.batteryPct
                            } else if (bodyJson.has("batteryPct")) {
                                member.batteryPct = bodyJson.optInt("batteryPct")
                            } else if (bodyJson.has("battery")) {
                                member.batteryPct = bodyJson.optInt("battery")
                            }
                        }
                    }
                    broadcastUpdate(couple)
                    sendResponse(output, 200, "OK", body = """{"status":"ok"}""")
                }

                // Set sharing
                path.matches(Regex("""^/api/couples/[^/]+/sharing$""")) && method == "POST" -> {
                    val coupleId = path.substringAfter("/api/couples/").substringBefore("/sharing")
                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found"}""")
                        return
                    }
                    val bodyJson = try {
                        JSONObject(bodyString)
                    } catch (_: JSONException) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }
                    val uid = bodyJson.optString("uid")
                    val on = bodyJson.optBoolean("sharingOn", true)

                    synchronized(couple) {
                        val member = couple.members[uid]
                        if (member != null) {
                            member.sharingOn = on
                        }
                    }
                    broadcastUpdate(couple)
                    sendResponse(output, 200, "OK", body = """{"status":"ok"}""")
                }

                // Set radius
                path.matches(Regex("""^/api/couples/[^/]+/radius$""")) && method == "POST" -> {
                    val coupleId = path.substringAfter("/api/couples/").substringBefore("/radius")
                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found"}""")
                        return
                    }
                    val bodyJson = try {
                        JSONObject(bodyString)
                    } catch (_: JSONException) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }
                    val radiusM = bodyJson.optInt("radiusM", 200)

                    synchronized(couple) {
                        couple.radiusM = radiusM
                    }
                    broadcastUpdate(couple)
                    sendResponse(output, 200, "OK", body = """{"status":"ok"}""")
                }

                // Update profile
                path.matches(Regex("""^/api/couples/[^/]+/profile$""")) && method == "POST" -> {
                    val coupleId = path.substringAfter("/api/couples/").substringBefore("/profile")
                    val couple = couplesById[coupleId]
                    if (couple == null) {
                        sendResponse(output, 404, "Not Found", body = """{"error":"Couple not found"}""")
                        return
                    }
                    val bodyJson = try {
                        JSONObject(bodyString)
                    } catch (_: JSONException) {
                        sendResponse(output, 400, "Bad Request", body = """{"error":"Invalid JSON"}""")
                        return
                    }
                    val uid = bodyJson.optString("uid")
                    val name = bodyJson.optString("name", "User")
                    val colorIndex = bodyJson.optInt("colorIndex", 0)

                    synchronized(couple) {
                        val member = couple.members[uid]
                        if (member != null) {
                            member.name = name
                            member.colorIndex = colorIndex
                        }
                    }
                    broadcastUpdate(couple)
                    sendResponse(output, 200, "OK", body = """{"status":"ok"}""")
                }

                else -> {
                    sendResponse(output, 404, "Not Found", body = """{"error":"Not found"}""")
                }
            }
        } catch (_: JSONException) {
            try {
                outputStream?.let { sendResponse(it, 400, "Bad Request", body = """{"error":"Invalid JSON"}""") }
            } catch (_: Exception) {}
        } catch (_: Exception) {
        } finally {
            if (!keepConnectionOpen) {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun broadcastUpdate(couple: ServerCouple) {
        val deadListeners = mutableListOf<StreamListener>()
        for (listener in couple.listeners) {
            try {
                val json = toCoupleStateJson(couple, listener.uid)
                val payload = "data: $json\n\n".toByteArray(Charsets.UTF_8)
                synchronized(listener.output) {
                    listener.output.write(payload)
                    listener.output.flush()
                }
            } catch (_: Exception) {
                deadListeners.add(listener)
            }
        }
        for (d in deadListeners) {
            couple.listeners.remove(d)
            try { d.socket.close() } catch (_: Exception) {}
        }
    }

    private fun toCoupleStateJson(couple: ServerCouple, callerUid: String): JSONObject {
        val members = couple.members.values.toList()
        val meMember = couple.members[callerUid]
            ?: members.firstOrNull()
            ?: ServerMember(callerUid, "Unknown", 0)
        val partnerMember = members.firstOrNull { it.uid != meMember.uid }

        return JSONObject().apply {
            put("coupleId", couple.coupleId)
            put("code", couple.code)
            put("radiusM", couple.radiusM)
            put("me", memberToJson(meMember))
            if (partnerMember != null) {
                put("partner", memberToJson(partnerMember))
            } else {
                put("partner", JSONObject.NULL)
            }
        }
    }

    private fun memberToJson(m: ServerMember): JSONObject = JSONObject().apply {
        put("uid", m.uid)
        put("name", m.name)
        put("colorIndex", m.colorIndex)
        put("sharingOn", m.sharingOn)
        val batt = m.batteryPct ?: m.lastLocation?.batteryPct
        if (batt != null) {
            put("batteryPct", batt)
            put("battery", batt)
        }
        val loc = m.lastLocation
        if (loc != null) {
            put("lastLocation", RelayJson.locationSampleToJson(loc))
        } else {
            put("lastLocation", JSONObject.NULL)
        }
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String = "application/json; charset=utf-8",
        body: String = "",
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }

    private fun readLineFromStream(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (baos.size() == 0) return null
                break
            }
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, Charsets.UTF_8)
            }
            baos.write(b)
            prev = b
        }
        return String(baos.toByteArray(), Charsets.UTF_8)
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in queryString.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                map[key] = value
            } else if (pair.isNotEmpty()) {
                map[pair] = ""
            }
        }
        return map
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val port = args.firstOrNull()?.toIntOrNull() ?: 8765
            val server = RelayServer(port)
            server.start()
            println("RelayServer listening on port ${server.localPort} (baseUrl=${server.baseUrl})...")
            Runtime.getRuntime().addShutdownHook(Thread {
                println("Stopping RelayServer...")
                server.stop()
            })
        }
    }
}
