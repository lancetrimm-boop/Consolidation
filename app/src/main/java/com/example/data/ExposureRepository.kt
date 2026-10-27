package com.example.data

import com.example.data.db.ExposureEventDao
import com.example.data.db.ExposureEventEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated Repository for recording typed Exposure events (database) and in-memory session exposures.
 *
 * Persists typed exposure events asynchronously and safely without blocking playback or UI.
 */
class ExposureRepository(
    private val exposureEventDao: ExposureEventDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    
    // In-memory session exposure tracking: mediaId -> exposureCount in current session
    private val sessionExposures = ConcurrentHashMap<String, Int>()

    /**
     * Records a typed exposure event asynchronously in background thread.
     */
    fun recordEvent(
        mediaId: String,
        eventType: ExposureEventType,
        milestone: ExposureProgressMilestone? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (mediaId.isBlank()) return
        scope.launch {
            recordEventSync(
                mediaId = mediaId,
                eventType = eventType,
                milestone = milestone,
                sessionId = sessionId,
                durationMs = durationMs,
                timestampMs = timestampMs
            )
        }
    }

    /**
     * Synchronous recording of typed exposure event.
     * Enforces milestone deduplication per session if milestone is specified.
     */
    suspend fun recordEventSync(
        mediaId: String,
        eventType: ExposureEventType,
        milestone: ExposureProgressMilestone? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(dispatcher) {
        if (mediaId.isBlank()) return@withContext false

        // Milestone deduplication check per session
        if (milestone != null && !sessionId.isNullOrBlank()) {
            val existing = exposureEventDao.getMilestoneEvent(
                mediaId = mediaId,
                sessionId = sessionId,
                milestone = milestone.percentage
            )
            if (existing != null) {
                return@withContext false // Already recorded this milestone for this session
            }
        }

        val entity = ExposureEventEntity(
            id = UUID.randomUUID().toString(),
            mediaId = mediaId,
            eventType = eventType,
            timestamp = timestampMs,
            milestone = milestone?.percentage,
            sessionId = sessionId,
            durationMs = durationMs
        )
        exposureEventDao.insertEvent(entity)
        
        // Track in session exposure map
        sessionExposures.compute(mediaId) { _, count -> (count ?: 0) + 1 }
        
        true
    }

    /**
     * Retrieves all recorded events for a given media item.
     */
    suspend fun getEventsForMedia(mediaId: String): List<ExposureEventEntity> = withContext(dispatcher) {
        exposureEventDao.getEventsForMedia(mediaId)
    }

    /**
     * Observes events for a media item as a Flow.
     */
    fun observeEventsForMedia(mediaId: String): Flow<List<ExposureEventEntity>> {
        return exposureEventDao.observeEventsForMedia(mediaId)
    }

    /**
     * Queries recent events in a time range for ranking / decay calculations.
     */
    suspend fun getRecentEvents(startTimeMs: Long): List<ExposureEventEntity> = withContext(dispatcher) {
        exposureEventDao.getRecentEvents(startTimeMs)
    }

    /**
     * Queries events within a start/end time window.
     */
    suspend fun getEventsInTimeRange(startTimeMs: Long, endTimeMs: Long): List<ExposureEventEntity> = withContext(dispatcher) {
        exposureEventDao.getEventsInTimeRange(startTimeMs, endTimeMs)
    }

    /**
     * Gets current session exposure count for a media item.
     */
    fun getSessionExposureCount(mediaId: String): Int {
        return sessionExposures[mediaId] ?: 0
    }

    /**
     * Returns a snapshot of all active session exposures.
     */
    fun getSessionExposuresSnapshot(): Map<String, Int> {
        return sessionExposures.toMap()
    }

    /**
     * In-memory soft reset for session exposures.
     * Does NOT delete persistent exposure events in database.
     */
    fun resetSessionExposures() {
        sessionExposures.clear()
    }

    /**
     * Cleanup old exposure events from DB.
     */
    suspend fun cleanupEventsOlderThan(beforeTimestampMs: Long): Int = withContext(dispatcher) {
        exposureEventDao.deleteEventsOlderThan(beforeTimestampMs)
    }

    fun close() {
        scope.cancel()
    }
}
