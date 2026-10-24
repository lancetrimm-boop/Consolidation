package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProgrammingFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: ProgrammingFeedbackEntity)

    @Query("SELECT * FROM programming_feedback WHERE channelId = :channelId ORDER BY timestamp DESC")
    suspend fun getFeedbackForChannel(channelId: String): List<ProgrammingFeedbackEntity>

    @Query("SELECT * FROM programming_feedback WHERE mediaId = :mediaId")
    suspend fun getFeedbackForMedia(mediaId: String): List<ProgrammingFeedbackEntity>

    @Query("SELECT * FROM programming_feedback")
    suspend fun getAllFeedback(): List<ProgrammingFeedbackEntity>
}
