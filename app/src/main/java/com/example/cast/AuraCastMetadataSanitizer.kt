package com.example.cast

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

object AuraCastMetadataSanitizer {
    private val EXTENSION_REGEX = Regex(
        """\.(mp4|mkv|mov|avi|webm|m4v|jpg|jpeg|png|gif|webp|bmp)(\s|$)""",
        RegexOption.IGNORE_CASE
    )

    fun sanitizeTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return "Media"
        return rawTitle.replace(EXTENSION_REGEX, "").trim().ifBlank { "Media" }
    }

    fun assertNoLeaks(metadata: MediaMetadata) {
        val combined = listOfNotNull(
            metadata.title?.toString(),
            metadata.subtitle?.toString(),
            metadata.description?.toString(),
            metadata.artist?.toString(),
            metadata.albumTitle?.toString(),
            metadata.albumArtist?.toString(),
            metadata.artworkUri?.toString()
        ).filter { it.isNotBlank() }.joinToString(" ")

        require(!combined.contains("file://", ignoreCase = true)) { "file:// URI leaked into Cast metadata" }
        require(!combined.contains("/storage/", ignoreCase = true)) { "/storage/ path leaked into Cast metadata" }
        require(!combined.contains("content://", ignoreCase = true)) { "content:// URI leaked into Cast metadata" }
        @Suppress("SdCardPath")
        require(!combined.contains("/sdcard/", ignoreCase = true)) { "/sdcard/ path leaked into Cast metadata" }
        require(!combined.contains("DCIM", ignoreCase = true)) { "DCIM path leaked into Cast metadata" }
        require(!combined.contains("Movies", ignoreCase = true)) { "Movies path leaked into Cast metadata" }
        require(!EXTENSION_REGEX.containsMatchIn(combined)) { "File extension leaked into Cast metadata" }
    }

    fun toSanitizedCastMediaItem(
        original: MediaItem,
        sanitizedId: String,
        sessionToken: String,
        localProxyBaseUrl: String,
        durationMs: Long,
        isVideo: Boolean,
        mimeType: String
    ): MediaItem {
        val cleanTitle = sanitizeTitle(original.mediaMetadata.title?.toString())
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(cleanTitle)
            .setDisplayTitle(cleanTitle)
            .setArtworkUri(Uri.parse("$localProxyBaseUrl/cast/$sessionToken/thumb/$sanitizedId"))

        if (isVideo) {
            metadataBuilder.setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
        }

        val metadata = metadataBuilder.build()
        assertNoLeaks(metadata)

        return MediaItem.Builder()
            .setMediaId(sanitizedId)
            .setUri(Uri.parse("$localProxyBaseUrl/cast/$sessionToken/stream/$sanitizedId"))
            .setMediaMetadata(metadata)
            .setMimeType(mimeType)
            .build()
    }
}
