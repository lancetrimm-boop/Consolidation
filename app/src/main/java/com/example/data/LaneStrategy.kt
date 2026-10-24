package com.example.data

import com.example.data.db.AISkipEventEntity
import com.example.data.db.ProgrammingFeedbackEntity
import com.example.data.metv.ExperienceRequest

/**
 * Programming context passed to channel strategies.
 */
data class ChannelProgrammingContext(
    val availableMedia: List<MediaItem>,
    val filterType: String = "VIDEOS",
    val tasteDNA: TasteDNA = TasteDNA(),
    val programmingFeedback: List<ProgrammingFeedbackEntity> = emptyList(),
    val exposureMap: Map<String, Int> = emptyMap(),
    val skipEvents: List<AISkipEventEntity> = emptyList(),
    val experienceRequest: ExperienceRequest? = null,
    val currentTimeMs: Long = System.currentTimeMillis(),
    val refreshEpoch: Int = 0,
    val sessionExposures: Map<String, Int> = emptyMap(),
    val decayedExposureMap: Map<String, Float> = emptyMap()
)

/**
 * Strategy interface for programming a channel block.
 */
interface LaneStrategy {
    fun programBlock(
        channel: Channel,
        context: ChannelProgrammingContext,
        limit: Int = 20
    ): List<MediaItem>
}
