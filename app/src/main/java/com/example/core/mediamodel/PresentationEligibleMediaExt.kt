package com.example.core.mediamodel

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem

fun PresentationEligibleMedia.toMediaItem(): MediaItem {
    val isVideo = mimeType.startsWith("video", ignoreCase = true)
    return MediaItem(
        id = id,
        title = title,
        mediaType = if (isVideo) "VIDEO" else "PHOTO",
        uriPath = uri,
        imageUrl = uri,
        durationMs = durationMs,
        width = width,
        height = height,
        dateAdded = dateAddedEpochMs,
        compatibilityStatus = CompatibilityStatus.PLAYABLE
    )
}
