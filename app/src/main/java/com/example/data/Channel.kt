package com.example.data

/**
 * Channel kind classification.
 */
enum class ChannelKind {
    FIXED,
    SEARCH_SEEDED
}

/**
 * Immutable domain model representing a personalized discovery Channel.
 */
data class Channel(
    val id: String,
    val title: String,
    val query: String = "",
    val referenceMediaIds: List<String> = emptyList(),
    val channelKind: ChannelKind = ChannelKind.FIXED,
    val strategyId: String = "ME_TV",
    val order: Int = 0,
    val isDefault: Boolean = false,
    val isProGated: Boolean = true
)

/**
 * UI State for an active Channel stream.
 */
sealed class ChannelState {
    object Idle : ChannelState()
    data class Loading(val channel: Channel) : ChannelState()
    data class Success(
        val channel: Channel,
        val items: List<MediaItem>
    ) : ChannelState()
    data class Error(
        val channel: Channel,
        val message: String
    ) : ChannelState()
}
