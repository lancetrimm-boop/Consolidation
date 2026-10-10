package com.example.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AuraCastServer(private val context: Context) {
    val sessionToken: String = UUID.randomUUID().toString().replace("-", "")
    private val mediaMap = ConcurrentHashMap<String, CastMediaSource>()
    private val thumbMap = ConcurrentHashMap<String, CastMediaSource>()
    private val running = AtomicBoolean(false)
    
    private var serverSocket: ServerSocket? = null
    var port: Int = 0
        private set

    private val executor = Executors.newCachedThreadPool()
    private var acceptThread: Thread? = null

    fun registerMedia(sanitizedId: String, source: CastMediaSource, thumbSource: CastMediaSource? = null) {
        require(sanitizedId.matches(Regex("[a-zA-Z0-9_-]+"))) { "Invalid sanitizedId format" }
        require(source.isSeekable()) { "CastMediaSource must prove random-access seekability before registration" }
        mediaMap[sanitizedId] = source
        thumbSource?.let { thumbMap[sanitizedId] = it }
    }

    fun unregisterMedia(sanitizedId: String) {
        mediaMap.remove(sanitizedId)
        thumbMap.remove(sanitizedId)
    }

    fun getLocalBaseUrl(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo.ipAddress
        val host = String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
        return "http://$host:$port"
    }

    @Synchronized
    fun start() {
        if (running.getAndSet(true)) return
        serverSocket = ServerSocket(0) // Dynamic port allocation by OS
        port = serverSocket!!.localPort
        Log.i(TAG, "AuraCastServer started on port $port with sessionToken=$sessionToken")

        acceptThread = Thread({
            while (running.get()) {
                try {
                    val socket = serverSocket!!.accept()
                    executor.execute { handle(socket) }
                } catch (e: IOException) {
                    if (running.get()) Log.w(TAG, "Server accept failed: ${e.message}")
                }
            }
        }, "AuraCastServerThread").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) return
        Log.i(TAG, "Stopping AuraCastServer and invalidating session token $sessionToken")
        try { serverSocket?.close() } catch (_: IOException) {}
        mediaMap.clear()
        thumbMap.clear()
        executor.shutdownNow()
        serverSocket = null
        port = 0
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val out = s.getOutputStream()
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeStatus(out, 400, "Bad Request")
                return
            }
            
            val method = parts[0]
            val path = parts[1].substringBefore("?")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            if (method != "GET" && method != "HEAD") {
                writeStatus(out, 405, "Method Not Allowed")
                return
            }

            val pathSegments = path.split("/").filter { it.isNotEmpty() }
            // Expected path format: /cast/{sessionToken}/stream/{sanitizedId} or /cast/{sessionToken}/thumb/{sanitizedId}
            if (pathSegments.size < 4 || pathSegments[0] != "cast" || pathSegments[1] != sessionToken) {
                writeStatus(out, 403, "Forbidden")
                return
            }

            val type = pathSegments[2]
            val id = pathSegments[3]
            val headOnly = method == "HEAD"

            when (type) {
                "stream" -> serveSource(out, mediaMap[id], headers["range"], headOnly)
                "thumb" -> serveSource(out, thumbMap[id], headers["range"], headOnly)
                else -> writeStatus(out, 404, "Not Found")
            }
            out.flush()
        }
    }

    private fun serveSource(out: OutputStream, source: CastMediaSource?, rangeHeader: String?, headOnly: Boolean) {
        if (source == null || !source.isSeekable() || source.length <= 0L) {
            writeStatus(out, 404, "Not Found")
            return
        }

        val totalLength = source.length
        var start = 0L
        var end = totalLength - 1
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val reqStart = range.getOrNull(0)?.toLongOrNull()
            val reqEnd = range.getOrNull(1)?.toLongOrNull()

            if (reqStart == null || reqStart >= totalLength) {
                val w = PrintWriter(out)
                w.print("HTTP/1.1 416 Range Not Satisfiable\r\n")
                w.print("Content-Range: bytes */$totalLength\r\n")
                w.print("Connection: close\r\n\r\n")
                w.flush()
                return
            }

            start = reqStart.coerceIn(0, totalLength - 1)
            end = reqEnd?.coerceIn(start, totalLength - 1) ?: (totalLength - 1)
            isPartial = true
        }

        val contentLength = end - start + 1
        val w = PrintWriter(out)
        if (isPartial) {
            w.print("HTTP/1.1 206 Partial Content\r\n")
            w.print("Content-Range: bytes $start-$end/$totalLength\r\n")
        } else {
            w.print("HTTP/1.1 200 OK\r\n")
        }
        w.print("Content-Type: ${source.mimeType}\r\n")
        w.print("Content-Length: $contentLength\r\n")
        w.print("Accept-Ranges: bytes\r\n")
        w.print("Connection: close\r\n\r\n")
        w.flush()

        if (headOnly) return

        try {
            source.writeRange(start, contentLength, out)
        } catch (e: Exception) {
            Log.w(TAG, "Error writing stream range [$start-$end] to client: ${e.message}")
        }
        out.flush()
    }

    private fun writeStatus(out: OutputStream, code: Int, message: String) {
        val w = PrintWriter(out)
        w.print("HTTP/1.1 $code $message\r\nConnection: close\r\n\r\n")
        w.flush()
    }

    companion object {
        private const val TAG = "AuraCastServer"
        
        fun mimeForFile(file: File, isThumb: Boolean = false): String {
            if (isThumb) return "image/jpeg"
            return when (file.extension.lowercase()) {
                "mp4", "m4v", "mov" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "video/mp4"
            }
        }
    }
}
