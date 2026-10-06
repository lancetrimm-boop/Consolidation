package com.example.data.metv

import com.example.data.MediaItem

/**
 * Domain model representing programming quality feedback.
 * Isolates algorithm feedback from item taste ratings.
 */
data class ProgrammingQualityFeedback(
    val id: String = java.util.UUID.randomUUID().toString(),
    val mediaId: String,
    val channelId: String,
    val isGoodProgramming: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Session-level aesthetic steering request ("Change the Mood").
 * Does NOT mutate stored TasteDNA.
 */
data class ExperienceRequest(
    val moodNudge: String,
    val aestheticWeights: Map<String, Float> = emptyMap()
)

/**
 * Coming-Up schedule representation for Me TV's "What's Next" guide.
 */
data class ComingUpSchedule(
    val currentItem: MediaItem?,
    val upcomingItems: List<MediaItem> = emptyList(),
    val aestheticExplanation: String = ""
)
