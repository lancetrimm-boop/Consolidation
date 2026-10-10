package com.example.cast

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class AuraCastServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `starts on dynamic port and serves full 200 OK file stream`() {
        val mockContext = mock(Context::class.java)
        val server = AuraCastServer(mockContext)
        server.start()

        assertTrue(server.port > 0)
        val token = server.sessionToken
        assertTrue(token.isNotBlank())

        val tempFile = tempFolder.newFile("sample_video.mp4").apply {
            writeBytes("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray())
        }
        val mediaSource = CastMediaSource.FileSource(tempFile, "video/mp4")
        val id = "video123"

        server.registerMedia(id, mediaSource)

        val url = URL("http://localhost:${server.port}/cast/$token/stream/$id")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        assertEquals(200, connection.responseCode)
        assertEquals("video/mp4", connection.contentType)
        assertEquals(36L, connection.contentLengthLong)

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        assertEquals("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ", responseBody)

        server.stop()
        assertEquals(0, server.port)
    }

    @Test
    fun `handles HTTP 206 Range request correctly`() {
        val mockContext = mock(Context::class.java)
        val server = AuraCastServer(mockContext)
        server.start()

        val token = server.sessionToken
        val tempFile = tempFolder.newFile("range_video.mp4").apply {
            writeBytes("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray())
        }
        val mediaSource = CastMediaSource.FileSource(tempFile, "video/mp4")
        val id = "range123"

        server.registerMedia(id, mediaSource)

        val url = URL("http://localhost:${server.port}/cast/$token/stream/$id")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=10-19")
        connection.connect()

        assertEquals(206, connection.responseCode)
        assertEquals("bytes 10-19/36", connection.getHeaderField("Content-Range"))
        assertEquals(10L, connection.contentLengthLong)

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        assertEquals("ABCDEFGHIJ", responseBody)

        server.stop()
    }

    @Test
    fun `rejects invalid session token with 403`() {
        val mockContext = mock(Context::class.java)
        val server = AuraCastServer(mockContext)
        server.start()

        val tempFile = tempFolder.newFile("sec_video.mp4").apply { writeText("data") }
        val mediaSource = CastMediaSource.FileSource(tempFile, "video/mp4")
        val id = "sec123"
        server.registerMedia(id, mediaSource)

        val url = URL("http://localhost:${server.port}/cast/invalidtoken/stream/$id")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        assertEquals(403, connection.responseCode)
        server.stop()
    }

    @Test
    fun `stop invalidates registrations and closes server socket`() {
        val mockContext = mock(Context::class.java)
        val server = AuraCastServer(mockContext)
        server.start()

        val port = server.port
        val token = server.sessionToken
        val tempFile = tempFolder.newFile("stop_video.mp4").apply { writeText("data") }
        val mediaSource = CastMediaSource.FileSource(tempFile, "video/mp4")
        val id = "stop123"
        server.registerMedia(id, mediaSource)

        server.stop()

        try {
            val url = URL("http://localhost:$port/cast/$token/stream/$id")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 500
            connection.connect()
            connection.responseCode
            assertFalse("Expected connection failure after server stop", true)
        } catch (_: Exception) {
            assertTrue(true)
        }
    }
}
