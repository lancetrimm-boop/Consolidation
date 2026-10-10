package com.example.cast

import android.content.Context
import android.net.Uri
import com.example.data.ConversionStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import java.io.File

data class ResolvedCastMedia(
    val mediaItem: MediaItem,
    val source: CastMediaSource,
    val thumbSource: CastMediaSource? = null,
    val isVideo: Boolean,
    val durationMs: Long
)

object AuraCastMediaResolver {

    fun resolveForCast(context: Context, repository: MediaRepository, item: MediaItem): ResolvedCastMedia? {
        // Gate 1 & Gate 2 Eligibility Boundary
        if (!repository.isItemEligibleForCandidates(item) || !repository.isItemVisibleInLibrary(item)) {
            return null
        }

        // Converted-file Preference Policy
        val preferConverted = item.conversionStatus == ConversionStatus.CONVERTED && !item.convertedUri.isNullOrBlank()
        val targetUriStr = if (preferConverted) item.convertedUri ?: "" else item.uriPath.ifBlank { item.imageUrl }

        if (targetUriStr.isBlank()) return null

        val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        val source: CastMediaSource? = createSeekableSource(context, targetUriStr, mimeType)
        
        // Fallback to original source if converted source is invalid/unreadable
        val finalSource = if (source != null && source.isSeekable()) {
            source
        } else if (preferConverted && item.uriPath.isNotBlank()) {
            val fallback = createSeekableSource(context, item.uriPath, mimeType)
            if (fallback != null && fallback.isSeekable()) fallback else null
        } else null

        if (finalSource == null || !finalSource.isSeekable()) {
            return null
        }

        val thumbSource = if (item.imageUrl.isNotBlank()) {
            createSeekableSource(context, item.imageUrl, "image/jpeg")
        } else null

        return ResolvedCastMedia(
            mediaItem = item,
            source = finalSource,
            thumbSource = thumbSource,
            isVideo = isVideo,
            durationMs = item.durationMs
        )
    }

    private fun createSeekableSource(context: Context, uriStr: String, mimeType: String): CastMediaSource? {
        if (uriStr.isBlank()) return null
        return try {
            val uri = Uri.parse(uriStr)
            val scheme = uri.scheme
            val isContentScheme = scheme.equals("content", ignoreCase = true)

            if (isContentScheme) {
                val source = CastMediaSource.ContentResolverSource(uri, context, mimeType)
                if (source.isSeekable()) source else null
            } else {
                val path = when {
                    uriStr.startsWith("file://") -> uri.path ?: uriStr.removePrefix("file://")
                    else -> uriStr
                }
                val file = File(path)
                if (file.exists() && file.canRead()) CastMediaSource.FileSource(file, mimeType) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
