package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExposureEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ExposureEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<ExposureEventEntity>)

    @Query("SELECT * FROM exposure_events WHERE mediaId = :mediaId ORDER BY timestamp DESC")
    suspend fun getEventsForMedia(mediaId: String): List<ExposureEventEntity>

    @Query("SELECT * FROM exposure_events WHERE mediaId = :mediaId ORDER BY timestamp DESC")
    fun observeEventsForMedia(mediaId: String): Flow<List<ExposureEventEntity>>

    @Query("SELECT * FROM exposure_events WHERE timestamp >= :startTimeMs AND timestamp <= :endTimeMs ORDER BY timestamp DESC")
    suspend fun getEventsInTimeRange(startTimeMs: Long, endTimeMs: Long): List<ExposureEventEntity>

    @Query("SELECT * FROM exposure_events WHERE timestamp >= :startTimeMs ORDER BY timestamp DESC")
    suspend fun getRecentEvents(startTimeMs: Long): List<ExposureEventEntity>

    @Query("SELECT * FROM exposure_events WHERE mediaId IN (:mediaIds) AND timestamp >= :startTimeMs ORDER BY timestamp DESC")
    suspend fun getRecentEventsForMediaBatch(mediaIds: List<String>, startTimeMs: Long): List<ExposureEventEntity>

    @Query("SELECT * FROM exposure_events WHERE mediaId = :mediaId AND sessionId = :sessionId AND milestone = :milestone LIMIT 1")
    suspend fun getMilestoneEvent(mediaId: String, sessionId: String, milestone: Int): ExposureEventEntity?

    @Query("DELETE FROM exposure_events WHERE timestamp < :beforeTimestampMs")
    suspend fun deleteEventsOlderThan(beforeTimestampMs: Long): Int

    @Query("SELECT COUNT(*) FROM exposure_events")
    suspend fun getTotalEventCount(): Int
}
