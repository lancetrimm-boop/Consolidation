package com.example.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuraCastSanitationTest {

    @Test
    fun `strips common video extensions`() {
        assertEquals("Vacation 2024", AuraCastMetadataSanitizer.sanitizeTitle("Vacation 2024.mp4"))
        assertEquals("Movie", AuraCastMetadataSanitizer.sanitizeTitle("Movie.MKV"))
        assertEquals("Clip", AuraCastMetadataSanitizer.sanitizeTitle("Clip.mov"))
        assertEquals("Sample Video", AuraCastMetadataSanitizer.sanitizeTitle("Sample Video.avi"))
        assertEquals("Web Stream", AuraCastMetadataSanitizer.sanitizeTitle("Web Stream.webm"))
    }

    @Test
    fun `strips image extensions`() {
        assertEquals("Photo", AuraCastMetadataSanitizer.sanitizeTitle("Photo.JPG"))
        assertEquals("Screenshot", AuraCastMetadataSanitizer.sanitizeTitle("Screenshot.png"))
        assertEquals("Graphic", AuraCastMetadataSanitizer.sanitizeTitle("Graphic.webp"))
    }

    @Test
    fun `never leaks file paths or extensions into metadata`() {
        val original = MediaItem.Builder()
            .setUri("file:///storage/emulated/0/DCIM/vacation.mp4")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("vacation.mp4")
                    .setSubtitle("Movies/vacation.mp4")
                    .setDescription("/storage/emulated/0/DCIM/vacation.mp4")
                    .build()
            )
            .build()

        val sanitized = AuraCastMetadataSanitizer.toSanitizedCastMediaItem(
            original = original,
            sanitizedId = "abc123token",
            sessionToken = "token99",
            localProxyBaseUrl = "http://192.168.1.10:8080",
            durationMs = 120_000L,
            isVideo = true,
            mimeType = "video/mp4"
        )

        val title = sanitized.mediaMetadata.title.toString()
        val uri = sanitized.localConfiguration?.uri.toString()

        assertFalse(title.contains(".mp4", ignoreCase = true))
        assertFalse(title.contains("/storage/"))
        assertFalse(title.contains("DCIM"))
        assertFalse(uri.contains("file://"))
        assertTrue(uri.startsWith("http://"))
        assertTrue(uri.contains("/cast/token99/stream/abc123token"))
        assertEquals("abc123token", sanitized.mediaId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertNoLeaks throws on raw storage path`() {
        AuraCastMetadataSanitizer.assertNoLeaks(
            MediaMetadata.Builder().setTitle("/storage/emulated/0/secret.mp4").build()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertNoLeaks throws on raw content URI`() {
        AuraCastMetadataSanitizer.assertNoLeaks(
            MediaMetadata.Builder().setTitle("content://media/external/video/123.mp4").build()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertNoLeaks throws on leaked extension`() {
        AuraCastMetadataSanitizer.assertNoLeaks(
            MediaMetadata.Builder().setTitle("Video.mkv").build()
        )
    }

    @Test
    fun `mimeForFile maps common extensions`() {
        assertEquals("video/mp4", AuraCastServer.mimeForFile(java.io.File("a.mp4")))
        assertEquals("video/mp4", AuraCastServer.mimeForFile(java.io.File("a.MOV")))
        assertEquals("video/x-matroska", AuraCastServer.mimeForFile(java.io.File("a.mkv")))
        assertEquals("video/webm", AuraCastServer.mimeForFile(java.io.File("a.webm")))
        assertEquals("image/jpeg", AuraCastServer.mimeForFile(java.io.File("a.png"), isThumb = true))
        assertEquals("image/png", AuraCastServer.mimeForFile(java.io.File("a.png")))
    }
}
