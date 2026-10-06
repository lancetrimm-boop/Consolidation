package com.example.data

import com.example.data.intelligence.IntelligenceRequest
import com.example.data.intelligence.IntelligenceMode
import com.example.data.intelligence.ContextualIntent

object RecommendationEngine {

    data class DiscoverCategories(
        val nextObsession: MediaItem?,
        val freshForYou: List<MediaItem>,
        val underTheRadar: List<MediaItem>,
        val aLittleDifferent: List<MediaItem>,
        val fromYourFavorites: List<MediaItem>,
        val continueWatching: MediaItem?,
        val wildcard: List<MediaItem> = emptyList(),
        val deepDiscovery: List<MediaItem> = emptyList()
    )

    suspend fun computeDiscoverCategories(
        repository: MediaRepository,
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap()
    ): DiscoverCategories {
        val core = repository.intelligenceCore
        if (core == null) return DiscoverCategories(null, emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), emptyList())
        
        // 1. Hero / Next Obsession
        val heroRequest = IntelligenceRequest(
            mode = IntelligenceMode.DISCOVER,
            contextualIntent = ContextualIntent.DISCOVER_CATEGORY,
            limit = 1,
            tasteDNA = tasteDNA,
            profile = profile,
            stats = stats,
            creatorProfiles = creatorProfiles
        )
        val heroResponse = core.processRequest(heroRequest)
        val nextObsession = heroResponse.candidates.firstOrNull()?.item

        // 2. Specialized Categories (Phased Core Requests)
        // For Update 9, we reuse the Core's DISCOVER mode with category-specific policies
        
        suspend fun getCategory(option: String, limit: Int): List<MediaItem> {
            val req = IntelligenceRequest(
                mode = IntelligenceMode.DISCOVER,
                contextualIntent = ContextualIntent.DISCOVER_CATEGORY,
                limit = limit,
                sortOption = option,
                tasteDNA = tasteDNA,
                profile = profile,
                stats = stats,
                creatorProfiles = creatorProfiles
            )
            return core.processRequest(req).candidates.map { it.item }
        }

        val freshForYou = getCategory("FRESH_FOR_YOU", 4)
        val fromYourFavorites = getCategory("FROM_YOUR_FAVORITES", 4)
        val wildcard = getCategory("WILDCARD", 3)
        val deepDiscovery = getCategory("DEEP_DISCOVERY", 3)
        val underTheRadar = getCategory("UNDER_THE_RADAR", 4)
        val aLittleDifferent = getCategory("A_LITTLE_DIFFERENT", 4)

        // 0. Continue Watching: Item in progress (Deterministic, preserved)
        val itemsOnly = repository.mediaItems.value.filter { 
            it.itemCount == null && 
            repository.isItemVisibleInLibrary(it)
        }
        val continueWatching = itemsOnly.filter { it.progress > 0f }.maxByOrNull { it.progress }

        return DiscoverCategories(
            nextObsession = nextObsession,
            freshForYou = freshForYou,
            underTheRadar = underTheRadar,
            aLittleDifferent = aLittleDifferent,
            fromYourFavorites = fromYourFavorites,
            continueWatching = continueWatching,
            wildcard = wildcard,
            deepDiscovery = deepDiscovery
        )
    }

    suspend fun computeObsessions(
        repository: MediaRepository,
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap()
    ): List<ObsessionRecommendation> {
        val categories = computeDiscoverCategories(repository, tasteDNA, profile, stats, creatorProfiles)
        val obsessions = mutableListOf<ObsessionRecommendation>()
        val seenMediaIds = mutableSetOf<String>()

        // 1. Hero / Next Obsession
        categories.nextObsession?.let { item ->
            seenMediaIds.add(item.id)
            obsessions.add(ObsessionRecommendation(
                id = "hero_${item.id}",
                title = "Your Next Obsession",
                subtitle = item.selectionReason ?: "Highest predicted match based on your vibe",
                strategy = ObsessionStrategy.Hero,
                previewItems = listOf(item),
                confidenceScore = 0.95f,
                emotionalRole = EmotionalRole.HIGH_CONFIDENCE
            ))
        }

        // 2. Fresh Arrivals
        val freshUnique = categories.freshForYou.filterNot { it.id in seenMediaIds }.take(3)
        if (freshUnique.isNotEmpty()) {
            freshUnique.forEach { seenMediaIds.add(it.id) }
            obsessions.add(ObsessionRecommendation(
                id = "fresh_arrivals",
                title = "Fresh Arrivals",
                subtitle = "Recently discovered content matching your evolving taste",
                strategy = ObsessionStrategy.FreshArrivals,
                previewItems = freshUnique,
                confidenceScore = 0.85f,
                emotionalRole = EmotionalRole.EMERGING_INTEREST
            ))
        }

        // 3. The Remix
        val remixUnique = categories.fromYourFavorites.filterNot { it.id in seenMediaIds }.take(3)
        if (remixUnique.isNotEmpty()) {
            remixUnique.forEach { seenMediaIds.add(it.id) }
            obsessions.add(ObsessionRecommendation(
                id = "fav_remix",
                title = "The Remix",
                subtitle = "New discoveries that feel like your saved favorites",
                strategy = ObsessionStrategy.FavoriteRemix,
                previewItems = remixUnique,
                confidenceScore = 0.80f,
                emotionalRole = EmotionalRole.DEEPENING
            ))
        }

        // 4. Under The Radar
        val radarUnique = categories.underTheRadar.filterNot { it.id in seenMediaIds }.take(3)
        if (radarUnique.isNotEmpty()) {
            radarUnique.forEach { seenMediaIds.add(it.id) }
            obsessions.add(ObsessionRecommendation(
                id = "under_radar",
                title = "Under The Radar",
                subtitle = "Hidden gems waiting to be discovered",
                strategy = ObsessionStrategy.FreshArrivals,
                previewItems = radarUnique,
                confidenceScore = 0.75f,
                emotionalRole = EmotionalRole.EMERGING_INTEREST
            ))
        }

        // 5. A Little Different
        val differentUnique = categories.aLittleDifferent.filterNot { it.id in seenMediaIds }.take(3)
        if (differentUnique.isNotEmpty()) {
            differentUnique.forEach { seenMediaIds.add(it.id) }
            obsessions.add(ObsessionRecommendation(
                id = "little_different",
                title = "A Little Different",
                subtitle = "Slightly outside your comfort zone",
                strategy = ObsessionStrategy.FreshArrivals,
                previewItems = differentUnique,
                confidenceScore = 0.70f,
                emotionalRole = EmotionalRole.EMERGING_INTEREST
            ))
        }

        return obsessions
    }

    /**
     * Specialized Pairwise Selection logic preserved in RecommendationEngine.
     * While candidate pools are now Core-driven, the specific information-value
     * and diversity logic for Pairwise selection remains here for the Update 9 MVP.
     */
    suspend fun getTop100PairwiseCandidates(
        repository: MediaRepository,
        winsMap: Map<String, Int> = emptyMap(),
        lossesMap: Map<String, Int> = emptyMap(),
        mediaTypeFilter: String = "ALL",
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        strategy: RecommendationStrategy? = null,
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap(),
        compareStrategy: CompareStrategy = CompareStrategy.PERSONALIZED,
        compareSort: CompareSortOption = CompareSortOption.RECOMMENDED,
        inputItems: List<MediaItem>? = null,
        seed: Long = 42L
    ): List<Pair<MediaItem, Float>> {
        val session = repository.compareSelectionSession.value
        val poolOverride = inputItems ?: if (session.isActive) {
            repository.mediaItems.value.filter { it.id in session.selectedIds }
        } else null

        val core = repository.intelligenceCore
        if (core != null) {
            try {
                val reqSortOption = when (compareStrategy) {
                    CompareStrategy.PERSONALIZED -> if (compareSort != CompareSortOption.RECOMMENDED) compareSort.name else "RANKING_REFINEMENT"
                    CompareStrategy.REDISCOVER -> "REDISCOVER"
                    CompareStrategy.LEAST_INTERACTED -> "LEAST_INTERACTED"
                    CompareStrategy.EXPLORE -> "EXPLORE"
                }

                val req = IntelligenceRequest(
                    mode = IntelligenceMode.SORT,
                    sortOption = reqSortOption,
                    filterType = mediaTypeFilter,
                    limit = 100, // Top 100 candidates for Pairwise selection
                    tasteDNA = tasteDNA,
                    profile = profile,
                    stats = stats,
                    creatorProfiles = creatorProfiles,
                    comparisonCounts = repository.getComparisonCounts(),
                    poolOverride = poolOverride,
                    seed = seed,
                    skipPersistence = true
                )
                val response = core.processRequest(req)
                if (response.isSuccess && response.candidates.isNotEmpty()) {
                    return response.candidates.map { it.item to it.rankScore.toFloat() }
                }
            } catch (e: Exception) {
                android.util.Log.w("RecommendationEngine", "IntelligenceCore processRequest failed, using fallback pool", e)
            }
        }
        
        // Final fallback (Safe degraded state - used in tests or when AI core fails or returns empty)
        val basePool = (inputItems ?: repository.mediaItems.value).filter { item ->
            if (item.isDeleted || item.itemCount != null) return@filter false
            val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("MOVIE", ignoreCase = true) || item.mediaType.startsWith("VIDEO", ignoreCase = true) || item.mediaType.startsWith("MOVIE", ignoreCase = true)
            if (isVideo) {
                com.example.compatibility.AuraPlaybackRouter.resolveRoute(item) is com.example.compatibility.PlaybackRouteResult.Playable
            } else {
                item.compatibilityStatus !in listOf(
                    CompatibilityStatus.CORRUPT,
                    CompatibilityStatus.UNREADABLE,
                    CompatibilityStatus.DELETED,
                    CompatibilityStatus.REPLACED
                ) && (item.uriPath.isNotBlank() || item.imageUrl.isNotBlank())
            }
        }.filter { item ->
            val type = item.mediaType.uppercase()
            when (mediaTypeFilter.uppercase()) {
                "PHOTO", "PHOTOS", "IMAGE", "IMAGES" -> type in listOf("PHOTO", "IMAGE") || type.startsWith("PHOTO") || type.startsWith("IMAGE")
                "VIDEO", "VIDEOS", "MOVIE", "MOVIES" -> type in listOf("VIDEO", "MOVIE") || type.startsWith("VIDEO") || type.startsWith("MOVIE")
                else -> true
            }
        }.filter { 
            val sessionIds = (repository as? MediaRepository)?.compareSelectionSession?.value?.selectedIds ?: emptySet()
            if (sessionIds.isEmpty()) true else sessionIds.contains(it.id)
        }
        
        val sortedPool = when (compareStrategy) {
            CompareStrategy.REDISCOVER -> basePool.sortedByDescending { it.rating }
            CompareStrategy.LEAST_INTERACTED -> basePool.sortedBy { it.viewCount }
            CompareStrategy.EXPLORE -> basePool.shuffled(java.util.Random(seed))
            else -> when (compareSort) {
                CompareSortOption.NEWEST -> basePool.sortedByDescending { it.dateAdded }
                CompareSortOption.OLDEST -> basePool.sortedBy { it.dateAdded }
                CompareSortOption.LARGEST_FILES -> basePool.sortedByDescending { it.sizeBytes }
                else -> basePool.sortedByDescending { it.rating }
            }
        }
        
        return sortedPool.take(100).map { it to 1.0f }
    }

    fun selectNextPairFromPool(
        top100Pool: List<Pair<MediaItem, Float>>,
        comparisonCounts: Map<String, Int> = emptyMap(),
        recentPairs: List<Pair<String, String>> = emptyList(),
        recentItemIds: List<String> = emptyList(),
        mediaTypeFilter: String = "ALL",
        randomSeed: Long = System.currentTimeMillis(),
        strategy: RecommendationStrategy? = null,
        tasteDNA: TasteDNA = TasteDNA(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap(),
        compareStrategy: CompareStrategy = CompareStrategy.PERSONALIZED
    ): Pair<MediaItem, MediaItem>? {
        val filteredPool = top100Pool.filter { (item, _) ->
            val type = item.mediaType.uppercase()
            when (mediaTypeFilter.uppercase()) {
                "PHOTO", "PHOTOS", "IMAGE", "IMAGES" -> type in listOf("PHOTO", "IMAGE") || type.startsWith("PHOTO") || type.startsWith("IMAGE")
                "VIDEO", "VIDEOS", "MOVIE", "MOVIES" -> type in listOf("VIDEO", "MOVIE") || type.startsWith("VIDEO") || type.startsWith("MOVIE")
                else -> true
            }
        }

        if (filteredPool.size < 2) return null

        val poolItems = filteredPool.map { it.first }
        var bestPair: Pair<MediaItem, MediaItem>? = null
        var maxScore = -1e9f

        val random = kotlin.random.Random(randomSeed)
        val maxIndex = filteredPool.size.coerceAtMost(30)
        
        for (i in 0 until maxIndex) {
            val itemA = poolItems[i]
            for (j in i + 1 until filteredPool.size) {
                val itemB = poolItems[j]
                
                val scoreA = filteredPool.find { it.first.id == itemA.id }?.second ?: 0f
                val scoreB = filteredPool.find { it.first.id == itemB.id }?.second ?: 0f
                val avgRelevance = (scoreA + scoreB) / 2.0f

                val expectedA = PairwiseEloEngine.calculateExpectedScore(itemA.eloRating, itemB.eloRating)
                val infoValue = PairwiseEloEngine.calculateInformationValue(expectedA).toFloat() * 100.0f
                val diversity = if (itemA.genre != itemB.genre || itemA.mediaType != itemB.mediaType) 1.5f else 0.0f

                val exactPairRepeat = recentPairs.any {
                    (it.first == itemA.id && it.second == itemB.id) || (it.first == itemB.id && it.second == itemA.id)
                }
                val repetitionPenalty = if (exactPairRepeat) 100.0f else 0.0f

                val recentCountA = recentItemIds.count { it == itemA.id }
                val recentCountB = recentItemIds.count { it == itemB.id }
                val itemRepetitionPenalty = (recentCountA + recentCountB) * 15.0f

                val totalValue = avgRelevance + infoValue + diversity - repetitionPenalty - itemRepetitionPenalty + (random.nextFloat() * 0.5f)

                if (totalValue > maxScore) {
                    maxScore = totalValue
                    bestPair = itemA to itemB
                }
            }
        }

        return bestPair ?: (poolItems[0] to poolItems[1])
    }
}
