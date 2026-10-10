package com.example.compatibility

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.CompatibilityStatus
import com.example.data.ConversionStatus
import com.example.data.MediaItem

sealed class PlaybackRouteResult {
    data class Playable(
        val playUri: String,
        val item: MediaItem,
        val isSoftwareDecode: Boolean = false,
        val containerFormat: String = "",
        val videoCodec: String = "",
        val audioCodec: String = ""
    ) : PlaybackRouteResult()

    data class NeedsConversion(
        val reason: String,
        val item: MediaItem
    ) : PlaybackRouteResult()

    data class Unsupported(
        val reason: String,
        val item: MediaItem
    ) : PlaybackRouteResult()

    data class Corrupt(
        val reason: String,
        val item: MediaItem
    ) : PlaybackRouteResult()
}

object AuraPlaybackRouter {

    fun resolveRoute(item: MediaItem, context: Context? = null): PlaybackRouteResult {
        Log.e("PlaylistTrace", "Resolving route for ${item.id} - Status: ${item.compatibilityStatus}, URI: ${item.uriPath}")
        // Preferred Playable URI (Converted URI if conversion succeeded)
        val targetUri = if (item.conversionStatus == ConversionStatus.CONVERTED && !item.convertedUri.isNullOrBlank()) {
            item.convertedUri
        } else if (item.uriPath.isNotBlank()) {
            item.uriPath
        } else {
            item.imageUrl
        }

        if (targetUri.isBlank()) {
            return PlaybackRouteResult.Corrupt(
                reason = "Media item has no valid URI path",
                item = item
            )
        }

        if (context != null && targetUri.startsWith("/") && !java.io.File(targetUri).exists()) {
            return PlaybackRouteResult.Corrupt(
                reason = "Local video file missing or unreadable",
                item = item
            )
        }

        if (targetUri.startsWith("content://") || targetUri.startsWith("file://")) {
            val ctx = context
            if (ctx != null) {
                try {
                    val uri = Uri.parse(targetUri)
                    ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use {}
                        ?: return PlaybackRouteResult.Corrupt(
                            reason = "Content URI inaccessible or missing",
                            item = item
                        )
                } catch (e: Exception) {
                    return PlaybackRouteResult.Corrupt(
                        reason = "Content URI inaccessible: ${e.message}",
                        item = item
                    )
                }
            }
        }

        // AURA PHASE 5: Handle Redirection for REPLACED items
        if (item.compatibilityStatus == CompatibilityStatus.REPLACED && !item.replacedByMediaId.isNullOrBlank()) {
            return PlaybackRouteResult.Corrupt(
                reason = "This file has been replaced by a compatible version.",
                item = item
            )
        }

        // If convertedUri is set and valid, media is guaranteed playable
        if (item.conversionStatus == ConversionStatus.CONVERTED && !item.convertedUri.isNullOrBlank()) {
            return PlaybackRouteResult.Playable(
                playUri = item.convertedUri,
                item = item,
                isSoftwareDecode = false,
                containerFormat = item.containerFormat.ifBlank { "MP4 (Universal)" },
                videoCodec = item.videoCodec.ifBlank { "video/avc" },
                audioCodec = item.audioCodec.ifBlank { "audio/mp4a-latm" }
            )
        }

        // Correct for stale persisted compatibility statuses where standard media was previously marked unsupported
        val standardSupportedVideoCodecs = setOf(
            "video/avc", "video/hevc", "video/mp4v-es",
            "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9",
            "video/av01", "video/3gpp"
        )
        val isStandardCodec = item.videoCodec.isNotBlank() && standardSupportedVideoCodecs.any { 
            item.videoCodec.equals(it, ignoreCase = true) || item.videoCodec.startsWith(it, ignoreCase = true) 
        }

        val isAvi = item.containerFormat.equals("AVI", true) || targetUri.lowercase().endsWith(".avi")

        val effectiveStatus = if (!isAvi && isStandardCodec && item.compatibilityStatus in setOf(
                CompatibilityStatus.UNSUPPORTED,
                CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
                CompatibilityStatus.NEEDS_TRANSCODE,
                CompatibilityStatus.UNTESTED,
                CompatibilityStatus.ANALYSIS_PENDING,
                CompatibilityStatus.ANALYSIS_FAILED
            )) {
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE
        } else {
            item.compatibilityStatus
        }

        return when (effectiveStatus) {
            CompatibilityStatus.PLAYABLE, CompatibilityStatus.THUMBNAIL_FAILED -> {
                PlaybackRouteResult.Playable(
                    playUri = targetUri,
                    item = item,
                    isSoftwareDecode = false,
                    containerFormat = item.containerFormat,
                    videoCodec = item.videoCodec,
                    audioCodec = item.audioCodec
                )
            }
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE -> {
                PlaybackRouteResult.Playable(
                    playUri = targetUri,
                    item = item,
                    isSoftwareDecode = true,
                    containerFormat = item.containerFormat,
                    videoCodec = item.videoCodec,
                    audioCodec = item.audioCodec
                )
            }
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION, CompatibilityStatus.NEEDS_TRANSCODE -> {
                PlaybackRouteResult.NeedsConversion(
                    reason = item.compatibilityReason.ifBlank { "This video format requires conversion for smooth playback on this device" },
                    item = item
                )
            }
            CompatibilityStatus.UNSUPPORTED, CompatibilityStatus.ANALYSIS_FAILED -> {
                PlaybackRouteResult.Unsupported(
                    reason = item.compatibilityReason.ifBlank { "Video codec or container format is not supported on this device" },
                    item = item
                )
            }
            CompatibilityStatus.CORRUPT, CompatibilityStatus.UNREADABLE, CompatibilityStatus.DELETED, CompatibilityStatus.REPLACED -> {
                PlaybackRouteResult.Corrupt(
                    reason = item.compatibilityReason.ifBlank { "Media file is corrupt, unreadable, missing, or has been replaced" },
                    item = item
                )
            }
            CompatibilityStatus.ANALYSIS_PENDING, 
            CompatibilityStatus.ANALYSIS_IN_PROGRESS, 
            CompatibilityStatus.UNTESTED -> {
                PlaybackRouteResult.Unsupported(
                    reason = "Media compatibility analysis is pending or untested",
                    item = item
                )
            }
        }
    }
}
