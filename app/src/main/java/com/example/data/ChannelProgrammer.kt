package com.example.data

import com.example.data.metv.MeTVProgrammingEngine
import java.util.Calendar
import java.util.Random

/**
 * Main Programmer for Aura Channels.
 * Maps channel definitions to their respective Lane Strategies and enforces 100% media-type homogeneity.
 */
class ChannelProgrammer(
    private val meTvEngine: MeTVProgrammingEngine = MeTVProgrammingEngine()
) {

    private val strategies: Map<String, LaneStrategy> = mapOf(
        "ME_TV" to MeTvLaneStrategy(meTvEngine),
        "FAVORITES" to FavoritesLaneStrategy(),
        "REDISCOVER" to RediscoverLaneStrategy(),
        "CONTINUE" to ContinueLaneStrategy(),
        "MOOD" to MoodLaneStrategy(),
        "NOVELTY" to NoveltyLaneStrategy(),
        "ERA" to EraLaneStrategy(),
        "DAYPARTS" to DaypartsLaneStrategy(),
        "SEASONAL_POPUPS" to SeasonalPopupsLaneStrategy(),
        "SCOPED_RANDOM" to ScopedRandomLaneStrategy(),
        "SEARCH_SEEDED" to SearchSeededLaneStrategy()
    )

    /**
     * Programs a block of media for the given channel.
     */
    fun programChannel(
        channel: Channel,
        context: ChannelProgrammingContext,
        limit: Int = 20
    ): List<MediaItem> {
        val strategy = strategies[channel.strategyId] ?: SearchSeededLaneStrategy()
        val rawItems = strategy.programBlock(channel, context, limit)

        val effectiveFilter = getEffectiveFilterType(channel, context)

        // Enforce strict media-type homogeneity
        val isVideoFilter = effectiveFilter.equals("VIDEO", ignoreCase = true) || effectiveFilter.equals("VIDEOS", ignoreCase = true) || effectiveFilter.equals("MOVIE", ignoreCase = true) || effectiveFilter.equals("MOVIES", ignoreCase = true)
        val isPhotoFilter = effectiveFilter.equals("PHOTO", ignoreCase = true) || effectiveFilter.equals("PHOTOS", ignoreCase = true) || effectiveFilter.equals("IMAGE", ignoreCase = true) || effectiveFilter.equals("IMAGES", ignoreCase = true)
        return rawItems.filter { item ->
            val isVid = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("MOVIE", ignoreCase = true) || item.mediaType.startsWith("VIDEO", ignoreCase = true) || item.mediaType.startsWith("MOVIE", ignoreCase = true)
            if (isVideoFilter) isVid else if (isPhotoFilter) !isVid else true
        }.take(limit)
    }

    /**
     * Helper to filter pool by media type.
     */
    internal companion object {
        fun getEffectiveFilterType(channel: Channel, context: ChannelProgrammingContext): String {
            if (channel.channelKind == ChannelKind.SEARCH_SEEDED) {
                val firstRefId = channel.referenceMediaIds.firstOrNull()
                if (firstRefId != null) {
                    val refItem = context.availableMedia.find { it.id == firstRefId }
                    if (refItem != null) {
                        val isVideo = refItem.mediaType.equals("VIDEO", ignoreCase = true) ||
                                refItem.mediaType.equals("MOVIE", ignoreCase = true) ||
                                refItem.mediaType.startsWith("VIDEO", ignoreCase = true) ||
                                refItem.mediaType.startsWith("MOVIE", ignoreCase = true)
                        return if (isVideo) "VIDEOS" else "PHOTOS"
                    }
                }
            }
            val filter = context.filterType
            val isVideoFilter = filter.equals("VIDEO", ignoreCase = true) || filter.equals("VIDEOS", ignoreCase = true)
            if (isVideoFilter) {
                val hasVideos = context.availableMedia.any {
                    it.mediaType.equals("VIDEO", ignoreCase = true) ||
                    it.mediaType.equals("MOVIE", ignoreCase = true) ||
                    it.mediaType.startsWith("VIDEO", ignoreCase = true) ||
                    it.mediaType.startsWith("MOVIE", ignoreCase = true)
                }
                if (!hasVideos && context.availableMedia.isNotEmpty()) {
                    return "ALL"
                }
            }
            return filter
        }

        fun filterPool(pool: List<MediaItem>, filterType: String): List<MediaItem> {
            val isVideoFilter = filterType.equals("VIDEO", ignoreCase = true) || filterType.equals("VIDEOS", ignoreCase = true) || filterType.equals("MOVIE", ignoreCase = true) || filterType.equals("MOVIES", ignoreCase = true)
            val isPhotoFilter = filterType.equals("PHOTO", ignoreCase = true) || filterType.equals("PHOTOS", ignoreCase = true) || filterType.equals("IMAGE", ignoreCase = true) || filterType.equals("IMAGES", ignoreCase = true)
            return pool.filter { item ->
                if (item.isDeleted) return@filter false
                val isVid = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("MOVIE", ignoreCase = true) || item.mediaType.startsWith("VIDEO", ignoreCase = true) || item.mediaType.startsWith("MOVIE", ignoreCase = true)
                if (isVideoFilter) isVid else if (isPhotoFilter) !isVid else true
            }
        }
    }
}

// --- LANE STRATEGY IMPLEMENTATIONS ---

/**
 * 1. Me TV Strategy — Delegates directly to existing authoritative MeTVProgrammingEngine.
 */
class MeTvLaneStrategy(
    private val meTvEngine: MeTVProgrammingEngine
) : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val result = meTvEngine.program(
            availableMedia = context.availableMedia,
            filterType = context.filterType,
            tasteDNA = context.tasteDNA,
            programmingFeedback = context.programmingFeedback,
            exposureMap = context.exposureMap,
            skipEvents = context.skipEvents,
            experienceRequest = context.experienceRequest,
            currentTimeMs = context.currentTimeMs,
            targetCount = limit,
            refreshEpoch = context.refreshEpoch,
            sessionExposures = context.sessionExposures,
            decayedExposureMap = context.decayedExposureMap
        )
        return result.items
    }
}

/**
 * 2. Unified Rediscover Strategy — Discovery + Rediscovery.
 * Combines low-exposure / unseen discovery candidates (Pool A) and forgotten previously-viewed candidates (>30 days unwatched, Pool B).
 * Interleaves candidates deterministically so neither pool suppresses the other when both contain viable media.
 */
class RediscoverLaneStrategy : LaneStrategy {
    private val REDISCOVER_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000

    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        if (pool.isEmpty()) return emptyList()

        val skipCounts = context.skipEvents.groupingBy { it.mediaId }.eachCount()

        // Pool A: Unseen & Low-Exposure Candidates (Novelty Discovery)
        val poolA = pool.map { item ->
            val exp = context.decayedExposureMap[item.id] ?: (context.exposureMap[item.id]?.toFloat() ?: item.getDecayedExposure(context.currentTimeMs))
            val skips = skipCounts[item.id] ?: 0
            val totalExp = exp + skips.toFloat()
            val score = 1.0f / (1.0f + totalExp)
            item to score
        }.sortedWith(compareByDescending<Pair<MediaItem, Float>> { it.second }.thenBy { it.first.id })
            .map { it.first }

        // Pool B: Forgotten Candidates (Exposed items viewed > 30 days ago)
        val poolB = pool.filter { item ->
            val exp = context.exposureMap[item.id] ?: 0
            val lastViewed = item.lastViewedTimestamp ?: 0L
            exp > 0 && (lastViewed == 0L || (context.currentTimeMs - lastViewed) > REDISCOVER_THRESHOLD_MS)
        }.sortedWith(compareBy<MediaItem> { it.lastViewedTimestamp ?: 0L }.thenBy { it.id })

        // Unified Composition: Interleave Pool B (Forgotten) and Pool A (Unseen/Low Exposure)
        val result = mutableListOf<MediaItem>()
        val seen = mutableSetOf<String>()

        var indexA = 0
        var indexB = 0

        while (result.size < limit && (indexA < poolA.size || indexB < poolB.size)) {
            if (indexB < poolB.size) {
                val candidateB = poolB[indexB++]
                if (seen.add(candidateB.id)) {
                    result.add(candidateB)
                }
            }
            if (result.size >= limit) break

            if (indexA < poolA.size) {
                val candidateA = poolA[indexA++]
                if (seen.add(candidateA.id)) {
                    result.add(candidateA)
                }
            }
        }

        return result.take(limit)
    }
}

/**
 * 3. Favorites Strategy — Favorited media with unwatched-first priority.
 */
class FavoritesLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        val favorites = pool.filter { it.isFavorite }
        if (favorites.isEmpty()) return emptyList()

        val skipCounts = context.skipEvents.groupingBy { it.mediaId }.eachCount()

        return favorites.map { item ->
            val rawExp = context.exposureMap[item.id] ?: 0
            val exp = (context.decayedExposureMap[item.id] ?: (context.exposureMap[item.id]?.toFloat() ?: item.getDecayedExposure(context.currentTimeMs))) + (skipCounts[item.id] ?: 0).toFloat() + (context.sessionExposures[item.id] ?: 0).toFloat()
            val score = if (rawExp == 0) 3.0f else (2.5f - (exp * 0.10f)).coerceAtMost(2.5f)
            item to score
        }.sortedWith(
            compareByDescending<Pair<MediaItem, Float>> { it.second }
                .thenBy { (it.first.id.hashCode() xor context.refreshEpoch).toString() }
        )
            .map { it.first }
            .take(limit)
    }
}

/**
 * 4. Mood Strategy — Evaluates TasteDNA visual dimensions + ExperienceRequest mood steering.
 */
class MoodLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        val nudge = context.experienceRequest?.moodNudge?.lowercase() ?: ""
        val skipCounts = context.skipEvents.groupingBy { it.mediaId }.eachCount()

        return pool.map { item ->
            val exp = (context.decayedExposureMap[item.id] ?: (context.exposureMap[item.id]?.toFloat() ?: item.getDecayedExposure(context.currentTimeMs))) + (skipCounts[item.id] ?: 0).toFloat() + (context.sessionExposures[item.id] ?: 0).toFloat()
            var score = 0.5f + (item.rating / 10.0f) - (exp * 0.08f)
            if (nudge.contains("energetic") && (item.genre.contains("Action", ignoreCase = true) || item.title.contains("Dynamic", ignoreCase = true))) {
                score += 0.3f
            } else if (nudge.contains("serene") && (item.genre.contains("Atmospheric", ignoreCase = true) || item.title.contains("Serene", ignoreCase = true))) {
                score += 0.3f
            } else if (nudge.contains("cinematic") && (item.genre.contains("Cinematic", ignoreCase = true) || item.title.contains("Vibrant", ignoreCase = true))) {
                score += 0.3f
            }
            item to score
        }.sortedWith(
            compareByDescending<Pair<MediaItem, Float>> { it.second }
                .thenBy { (it.first.id.hashCode() xor context.refreshEpoch).toString() }
        )
            .map { it.first }
            .take(limit)
    }
}

/**
 * 5. Continue Strategy — Serialized series continue, selecting oldest unwatched episode in order.
 */
class ContinueLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        val serializedGroups = pool.filter { !it.parentContentId.isNullOrBlank() }.groupBy { it.parentContentId!! }

        val result = mutableListOf<MediaItem>()
        serializedGroups.forEach { (_, group) ->
            val oldestUnwatched = group.filter { ((context.decayedExposureMap[it.id] ?: (context.exposureMap[it.id]?.toFloat() ?: it.getDecayedExposure(context.currentTimeMs))) + (context.sessionExposures[it.id] ?: 0).toFloat()) < 0.1f }
                .sortedWith(compareBy<MediaItem> { it.selectionReason ?: "" }.thenBy { it.id })
                .firstOrNull() ?: group.sortedWith(compareBy<MediaItem> { it.selectionReason ?: "" }.thenBy { it.id }).firstOrNull()
            if (oldestUnwatched != null) {
                result.add(oldestUnwatched)
            }
        }
        return result.take(limit)
    }
}

/**
 * Obsolete Strategy implementations retained for internal fallback/backwards-compatibility testing.
 */
class NoveltyLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        val skipCounts = context.skipEvents.groupingBy { it.mediaId }.eachCount()

        return pool.map { item ->
            val exp = context.decayedExposureMap[item.id] ?: (context.exposureMap[item.id]?.toFloat() ?: item.getDecayedExposure(context.currentTimeMs))
            val skips = skipCounts[item.id] ?: 0
            val totalExp = exp + skips.toFloat()
            val score = 1.0f / (1.0f + totalExp)
            item to score
        }.sortedWith(compareByDescending<Pair<MediaItem, Float>> { it.second }.thenBy { it.first.id })
            .map { it.first }
            .take(limit)
    }
}

class EraLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        return pool.sortedWith(compareByDescending<MediaItem> { it.year }.thenBy { it.id })
            .take(limit)
    }
}

class DaypartsLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        val calendar = Calendar.getInstance().apply { timeInMillis = context.currentTimeMs }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val daypart = when (hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..22 -> "EVENING"
            else -> "NIGHT"
        }

        return pool.map { item ->
            val boost = when (daypart) {
                "EVENING", "NIGHT" -> if (item.genre.contains("Cinematic", ignoreCase = true) || item.genre.contains("Action", ignoreCase = true)) 0.2f else 0.0f
                "MORNING" -> if (item.genre.contains("Atmospheric", ignoreCase = true) || item.genre.contains("Serene", ignoreCase = true)) 0.2f else 0.0f
                else -> 0.1f
            }
            item to (0.5f + boost)
        }.sortedWith(compareByDescending<Pair<MediaItem, Float>> { it.second }.thenBy { it.first.id })
            .map { it.first }
            .take(limit)
    }
}

class SeasonalPopupsLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        return pool.sortedWith(compareByDescending<MediaItem> { it.rating }.thenBy { it.id })
            .take(limit)
    }
}

class ScopedRandomLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val pool = ChannelProgrammer.filterPool(context.availableMedia, context.filterType)
        val seed = context.currentTimeMs + channel.id.hashCode()
        val random = Random(seed)
        return pool.shuffled(random).take(limit)
    }
}

/**
 * Search-Seeded Strategy — Dynamic block programming from saved search seeds (text query + visual references).
 */
class SearchSeededLaneStrategy : LaneStrategy {
    override fun programBlock(channel: Channel, context: ChannelProgrammingContext, limit: Int): List<MediaItem> {
        val effectiveFilter = ChannelProgrammer.getEffectiveFilterType(channel, context)
        val pool = ChannelProgrammer.filterPool(context.availableMedia, effectiveFilter)
        if (pool.isEmpty()) return emptyList()

        val query = channel.query.lowercase().trim()
        val refIds = channel.referenceMediaIds.toSet()
        val skipCounts = context.skipEvents.groupingBy { it.mediaId }.eachCount()

        val scored = pool.map { item ->
            val exp = (context.decayedExposureMap[item.id] ?: (context.exposureMap[item.id]?.toFloat() ?: item.getDecayedExposure(context.currentTimeMs))) + (skipCounts[item.id] ?: 0).toFloat() + (context.sessionExposures[item.id] ?: 0).toFloat()
            var score = 0.5f - (exp * 0.08f)
            if (query.isNotEmpty()) {
                if (item.title.lowercase().contains(query)) score += 0.4f
                if (item.genre.lowercase().contains(query)) score += 0.3f
            }
            if (refIds.contains(item.id)) {
                score += 0.5f
            }
            item to score
        }

        return scored.sortedWith(
            compareByDescending<Pair<MediaItem, Float>> { it.second }
                .thenBy { (it.first.id.hashCode() xor context.refreshEpoch).toString() }
        )
            .map { it.first }
            .take(limit)
    }
}
