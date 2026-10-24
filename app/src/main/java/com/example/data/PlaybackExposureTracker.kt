package com.example.data

import java.util.UUID

/**
 * Encapsulates playback exposure tracking logic for Video and Photo/Slideshow playback sessions.
 * Manages stable session IDs, duration thresholds, progress milestones, and completion.
 */
class PlaybackExposureTracker(
    val mediaId: String,
    val isVideo: Boolean,
    val exposureRepository: ExposureRepository,
    val sessionId: String = UUID.randomUUID().toString()
) {
    private var hasStarted = false
    private var hasCompleted = false

    /**
     * Updates video playback progress.
     * @param currentPositionMs Current playback position in milliseconds.
     * @param durationMs Total media duration in milliseconds.
     * @param isPlaying Whether the player is currently actively playing.
     * @param isEnded Whether the player has reached STATE_ENDED.
     */
    fun updateVideoProgress(
        currentPositionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        isEnded: Boolean = false
    ) {
        if (!isVideo || mediaId.isBlank()) return

        // 1. PLAYBACK_STARTED (3 seconds continuous playback)
        if (!hasStarted && isPlaying && currentPositionMs >= 3000L) {
            hasStarted = true
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_STARTED,
                sessionId = sessionId,
                durationMs = durationMs
            )
        }

        // Progress & Completion require valid duration > 0
        if (durationMs <= 0L) return

        // 2. PLAYBACK_PROGRESS milestones (25%, 50%, 75%)
        val progressRatio = currentPositionMs.toDouble() / durationMs.toDouble()

        if (progressRatio >= 0.25) {
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_PROGRESS,
                milestone = ExposureProgressMilestone.PERCENT_25,
                sessionId = sessionId,
                durationMs = durationMs
            )
        }
        if (progressRatio >= 0.50) {
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_PROGRESS,
                milestone = ExposureProgressMilestone.PERCENT_50,
                sessionId = sessionId,
                durationMs = durationMs
            )
        }
        if (progressRatio >= 0.75) {
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_PROGRESS,
                milestone = ExposureProgressMilestone.PERCENT_75,
                sessionId = sessionId,
                durationMs = durationMs
            )
        }

        // 3. PLAYBACK_COMPLETED (STATE_ENDED or >= 90% duration)
        if (!hasCompleted && (isEnded || progressRatio >= 0.90)) {
            hasCompleted = true
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_COMPLETED,
                sessionId = sessionId,
                durationMs = durationMs
            )
        }
    }

    /**
     * Updates photo/slideshow presentation time.
     * @param elapsedTimeMs Elapsed time in current photo slide presentation.
     * @param targetDurationMs Configured slide duration (e.g. 3000ms, 4000ms, 5000ms).
     */
    fun updatePhotoProgress(
        elapsedTimeMs: Long,
        targetDurationMs: Long
    ) {
        if (isVideo || mediaId.isBlank() || targetDurationMs <= 0L) return

        val startThresholdMs = if (targetDurationMs <= 4000L) {
            targetDurationMs / 2
        } else {
            3000L
        }

        // 1. PLAYBACK_STARTED
        if (!hasStarted && elapsedTimeMs >= startThresholdMs) {
            hasStarted = true
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_STARTED,
                sessionId = sessionId,
                durationMs = targetDurationMs
            )
        }

        // 2. PLAYBACK_COMPLETED at full configured duration
        if (!hasCompleted && elapsedTimeMs >= targetDurationMs) {
            hasCompleted = true
            exposureRepository.recordEvent(
                mediaId = mediaId,
                eventType = ExposureEventType.PLAYBACK_COMPLETED,
                sessionId = sessionId,
                durationMs = targetDurationMs
            )
        }
    }
}
