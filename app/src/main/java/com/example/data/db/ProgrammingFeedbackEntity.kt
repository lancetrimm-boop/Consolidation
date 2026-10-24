package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for programming quality feedback ("Good/Bad programming").
 * Strictly isolated from UserPreferenceEntity, ratings, and TasteDNA.
 */
@Entity(tableName = "programming_feedback")
data class ProgrammingFeedbackEntity(
    @PrimaryKey
    val id: String,
    val mediaId: String,
    val channelId: String,
    val isGoodProgramming: Boolean,
    val timestamp: Long
)
