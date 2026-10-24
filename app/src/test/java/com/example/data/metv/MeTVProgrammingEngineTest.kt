package com.example.data.metv

import com.example.data.MediaItem
import com.example.data.TasteDNA
import com.example.data.db.AISkipEventEntity
import com.example.data.db.ProgrammingFeedbackEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MeTVProgrammingEngineTest {

    private lateinit var engine: MeTVProgrammingEngine

    @Before
    fun setUp() {
        engine = MeTVProgrammingEngine()
    }

    private fun createMediaItem(
        id: String,
        title: String,
        isVideo: Boolean = true,
        isFavorite: Boolean = false,
        lastViewed: Long? = null,
        parentContentId: String? = null,
        selectionReason: String? = null,
        genre: String = "Cinematic"
    ): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = if (isVideo) "VIDEO" else "PHOTO",
            year = 2024,
            duration = if (isVideo) "02:00" else "",
            genre = genre,
            isFavorite = isFavorite,
            lastViewedTimestamp = lastViewed,
            parentContentId = parentContentId,
            selectionReason = selectionReason
        )
    }

    @Test
    fun testNoveltyLane_PrefersUnseenCandidates() {
        val unseen = createMediaItem("v1", "Unseen Video")
        val exposed = createMediaItem("v2", "Exposed Video")
        val pool = listOf(unseen, exposed)
        val exposureMap = mapOf("v2" to 5)

        val result = engine.program(availableMedia = pool, exposureMap = exposureMap)

        assertEquals("v1", result.items[0].id)
        assertTrue(result.laneContributions[MeTVProgrammingEngine.LANE_NOVELTY]?.contains("v1") == true)
    }

    @Test
    fun testExposurePenalty_ReducesPriorityOnRepeatedExposures() {
        val lowExp = createMediaItem("v1", "Low Exposure")
        val highExp = createMediaItem("v2", "High Exposure")
        val pool = listOf(highExp, lowExp)
        val exposureMap = mapOf("v1" to 1, "v2" to 10)

        val result = engine.program(availableMedia = pool, exposureMap = exposureMap)

        assertEquals("v1", result.items[0].id)
    }

    @Test
    fun testSkipEvents_CountAsExposures() {
        val item1 = createMediaItem("v1", "Skipped Item")
        val item2 = createMediaItem("v2", "Clean Item")
        val pool = listOf(item1, item2)
        val skipEvents = listOf(
            AISkipEventEntity(id = 1L, mediaId = "v1", eventType = "SKIP_FORWARD", fromPosMs = 1000L, toPosMs = 5000L, timestamp = System.currentTimeMillis())
        )

        val result = engine.program(availableMedia = pool, skipEvents = skipEvents)

        assertEquals("v2", result.items[0].id)
    }

    @Test
    fun testRediscoverLane_SelectsForgottenMediaOlderThan30Days() {
        val now = System.currentTimeMillis()
        val thirtyOneDaysAgo = now - (31L * 24 * 60 * 60 * 1000)
        val recent = createMediaItem("v1", "Recent", lastViewed = now)
        val forgotten = createMediaItem("v2", "Forgotten", lastViewed = thirtyOneDaysAgo)
        val pool = listOf(recent, forgotten)
        val exposureMap = mapOf("v1" to 2, "v2" to 2)

        val result = engine.program(availableMedia = pool, exposureMap = exposureMap, currentTimeMs = now)

        assertTrue(result.laneContributions[MeTVProgrammingEngine.LANE_REDISCOVER]?.contains("v2") == true)
        assertEquals("v2", result.items[0].id)
    }

    @Test
    fun testFavoritesLane_PrioritizesUnwatchedFavorites() {
        val favUnwatched = createMediaItem("f1", "Fav Unwatched", isFavorite = true)
        val favWatched = createMediaItem("f2", "Fav Watched", isFavorite = true)
        val normal = createMediaItem("n1", "Normal")
        val pool = listOf(normal, favWatched, favUnwatched)
        val exposureMap = mapOf("f2" to 3, "n1" to 0)

        val result = engine.program(availableMedia = pool, exposureMap = exposureMap)

        assertEquals("f1", result.items[0].id)
        assertTrue(result.laneContributions[MeTVProgrammingEngine.LANE_FAVORITES]?.contains("f1") == true)
    }

    @Test
    fun testMoodSteering_ChangesSessionProgrammingWithoutMutatingTasteDNA() {
        val initialTasteDNA = TasteDNA(vibrancy = 0.5, motion = 0.5)
        val energetic = createMediaItem("v1", "Dynamic Action", genre = "Action")
        val serene = createMediaItem("v2", "Serene Landscape", genre = "Atmospheric")
        val pool = listOf(serene, energetic)

        val request = ExperienceRequest(moodNudge = "More Energetic")
        val result = engine.program(availableMedia = pool, experienceRequest = request, tasteDNA = initialTasteDNA)

        assertEquals("v1", result.items[0].id)
        // Confirm baseline TasteDNA remains 100% unaltered
        assertEquals(0.5, initialTasteDNA.vibrancy, 0.001)
        assertEquals(0.5, initialTasteDNA.motion, 0.001)
    }

    @Test
    fun testContinueLane_PreservesEpisodeOrderForSerializedContent() {
        val ep1 = createMediaItem("e1", "Ep 1", parentContentId = "series_a", selectionReason = "01")
        val ep2 = createMediaItem("e2", "Ep 2", parentContentId = "series_a", selectionReason = "02")
        val pool = listOf(ep2, ep1)

        val result = engine.program(availableMedia = pool)

        val continueLaneIds = result.laneContributions[MeTVProgrammingEngine.LANE_CONTINUE]
        assertNotNull(continueLaneIds)
        assertEquals("e1", continueLaneIds!![0])
    }

    @Test
    fun testProgrammingFeedback_Isolation_AppliesPenaltyWithoutMutatingItemRatings() {
        val item1 = createMediaItem("v1", "Item 1")
        val item2 = createMediaItem("v2", "Item 2")
        val pool = listOf(item1, item2)

        val negativeFeedback = listOf(
            ProgrammingFeedbackEntity(id = "fb1", mediaId = "v1", channelId = "main", isGoodProgramming = false, timestamp = System.currentTimeMillis())
        )

        val result = engine.program(availableMedia = pool, programmingFeedback = negativeFeedback)

        assertEquals("v2", result.items[0].id)
        // Item rating remains untouched
        assertEquals(0f, item1.rating, 0.001f)
    }

    @Test
    fun testMediaTypeHomogeneity_VideosFilterContainsZeroPhotos() {
        val photo1 = createMediaItem("p1", "Photo 1", isVideo = false)
        val video1 = createMediaItem("v1", "Video 1", isVideo = true)
        val pool = listOf(photo1, video1)

        val result = engine.program(availableMedia = pool, filterType = "VIDEOS")

        assertEquals(1, result.items.size)
        assertEquals("v1", result.items[0].id)
        assertTrue(result.items.none { it.mediaType == "PHOTO" })
    }

    @Test
    fun testMediaTypeHomogeneity_PhotosFilterContainsZeroVideos() {
        val photo1 = createMediaItem("p1", "Photo 1", isVideo = false)
        val video1 = createMediaItem("v1", "Video 1", isVideo = true)
        val pool = listOf(photo1, video1)

        val result = engine.program(availableMedia = pool, filterType = "PHOTOS")

        assertEquals(1, result.items.size)
        assertEquals("p1", result.items[0].id)
        assertTrue(result.items.none { it.mediaType == "VIDEO" })
    }

    @Test
    fun testEmptyAndSparseLibrary_ReturnsSafeResult() {
        val emptyResult = engine.program(availableMedia = emptyList())
        assertTrue(emptyResult.items.isEmpty())

        val singleItem = createMediaItem("v1", "Single")
        val singleResult = engine.program(availableMedia = listOf(singleItem))
        assertEquals(1, singleResult.items.size)
        assertEquals("v1", singleResult.items[0].id)
    }

    @Test
    fun testDeterminism_IdenticalInputsProduceIdenticalOutput() {
        val item1 = createMediaItem("v1", "Video 1")
        val item2 = createMediaItem("v2", "Video 2")
        val pool = listOf(item2, item1)

        val run1 = engine.program(availableMedia = pool, currentTimeMs = 1000L)
        val run2 = engine.program(availableMedia = pool, currentTimeMs = 1000L)

        assertEquals(run1.items.map { it.id }, run2.items.map { it.id })
    }
}
