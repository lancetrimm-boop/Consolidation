package com.example.data

import com.example.data.metv.ExperienceRequest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelProgrammerTest {

    private lateinit var programmer: ChannelProgrammer

    @Before
    fun setUp() {
        programmer = ChannelProgrammer()
    }

    private fun createMediaItem(
        id: String,
        title: String,
        isVideo: Boolean = true,
        isFavorite: Boolean = false,
        lastViewed: Long? = null,
        parentContentId: String? = null,
        selectionReason: String? = null,
        genre: String = "Cinematic",
        year: Int = 2024
    ): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = if (isVideo) "VIDEO" else "PHOTO",
            year = year,
            duration = if (isVideo) "01:30" else "",
            genre = genre,
            isFavorite = isFavorite,
            lastViewedTimestamp = lastViewed,
            parentContentId = parentContentId,
            selectionReason = selectionReason
        )
    }

    @Test
    fun testAllFixedChannelsResolveToStrategy() {
        val channels = ChannelRegistry.allFixedChannels()
        val media = listOf(
            createMediaItem("v1", "Fav Video", isFavorite = true, parentContentId = "series_1")
        )
        val context = ChannelProgrammingContext(availableMedia = media, filterType = "VIDEOS")

        assertEquals(5, channels.size)
        channels.forEach { channel ->
            val result = programmer.programChannel(channel, context)
            assertNotNull("Channel ${channel.id} returned null result", result)
            assertFalse("Channel ${channel.id} failed to return items", result.isEmpty())
        }
    }

    @Test
    fun testMeTvStrategy_DelegatesToMeTvEngine() {
        val meTvChannel = ChannelRegistry.get("ME_TV")!!
        val media = listOf(createMediaItem("v1", "Video 1"), createMediaItem("v2", "Video 2"))
        val context = ChannelProgrammingContext(availableMedia = media, filterType = "VIDEOS")

        val result = programmer.programChannel(meTvChannel, context)
        assertEquals(2, result.size)
        assertEquals("v1", result[0].id)
    }

    @Test
    fun testUnifiedRediscoverStrategy_PrioritizesForgottenAndUnseenMedia() {
        val rediscoverChannel = ChannelRegistry.get("REDISCOVER")!!
        val now = System.currentTimeMillis()
        val thirtyOneDaysAgo = now - (31L * 24 * 60 * 60 * 1000)
        val recent = createMediaItem("v1", "Recent", lastViewed = now)
        val forgotten = createMediaItem("v2", "Forgotten", lastViewed = thirtyOneDaysAgo)
        val unseen = createMediaItem("v3", "Unseen")
        val media = listOf(recent, forgotten, unseen)
        val context = ChannelProgrammingContext(
            availableMedia = media,
            exposureMap = mapOf("v1" to 2, "v2" to 2),
            currentTimeMs = now
        )

        val result = programmer.programChannel(rediscoverChannel, context)
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.id == "v2" || it.id == "v3" })
    }

    @Test
    fun testFavoritesStrategy_ExcludesNonFavoritesAndPrioritizesUnwatched() {
        val favChannel = ChannelRegistry.get("FAVORITES")!!
        val favUnwatched = createMediaItem("f1", "Fav Unwatched", isFavorite = true)
        val favWatched = createMediaItem("f2", "Fav Watched", isFavorite = true)
        val normal = createMediaItem("n1", "Normal", isFavorite = false)
        val media = listOf(normal, favWatched, favUnwatched)
        val context = ChannelProgrammingContext(
            availableMedia = media,
            exposureMap = mapOf("f2" to 3, "n1" to 0)
        )

        val result = programmer.programChannel(favChannel, context)
        assertEquals(2, result.size)
        assertEquals("f1", result[0].id)
        assertTrue(result.none { it.id == "n1" })
    }

    @Test
    fun testMoodStrategy_AppliesMoodNudgeWithoutMutatingTasteDNA() {
        val moodChannel = ChannelRegistry.get("MOOD")!!
        val initialTasteDNA = TasteDNA(vibrancy = 0.5)
        val energetic = createMediaItem("v1", "Dynamic Action", genre = "Action")
        val serene = createMediaItem("v2", "Serene Landscape", genre = "Atmospheric")
        val media = listOf(serene, energetic)
        val context = ChannelProgrammingContext(
            availableMedia = media,
            tasteDNA = initialTasteDNA,
            experienceRequest = ExperienceRequest("More Energetic")
        )

        val result = programmer.programChannel(moodChannel, context)
        assertEquals("v1", result[0].id)
        assertEquals(0.5, initialTasteDNA.vibrancy, 0.001)
    }

    @Test
    fun testContinueStrategy_PreservesEpisodeOrder() {
        val continueChannel = ChannelRegistry.get("CONTINUE")!!
        val ep1 = createMediaItem("e1", "Ep 1", parentContentId = "series_a", selectionReason = "01")
        val ep2 = createMediaItem("e2", "Ep 2", parentContentId = "series_a", selectionReason = "02")
        val media = listOf(ep2, ep1)
        val context = ChannelProgrammingContext(availableMedia = media)

        val result = programmer.programChannel(continueChannel, context)
        assertEquals(1, result.size)
        assertEquals("e1", result[0].id)
    }

    @Test
    fun testMediaTypeHomogeneity_EnforcedAcrossAll5Channels() {
        val photo1 = createMediaItem("p1", "Photo 1", isVideo = false, isFavorite = true, parentContentId = "series_p")
        val video1 = createMediaItem("v1", "Video 1", isVideo = true, isFavorite = true, parentContentId = "series_v")
        val media = listOf(photo1, video1)

        val videoContext = ChannelProgrammingContext(availableMedia = media, filterType = "VIDEOS")
        val photoContext = ChannelProgrammingContext(availableMedia = media, filterType = "PHOTOS")

        ChannelRegistry.allFixedChannels().forEach { channel ->
            val videoResult = programmer.programChannel(channel, videoContext)
            assertTrue("Channel ${channel.id} VIDEOS contained non-videos", videoResult.all { it.mediaType == "VIDEO" })

            val photoResult = programmer.programChannel(channel, photoContext)
            assertTrue("Channel ${channel.id} PHOTOS contained non-photos", photoResult.all { it.mediaType == "PHOTO" })
        }
    }

    @Test
    fun testEmptyLibrary_ReturnsSafeEmptyResult() {
        val context = ChannelProgrammingContext(availableMedia = emptyList())
        ChannelRegistry.allFixedChannels().forEach { channel ->
            val result = programmer.programChannel(channel, context)
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun testExposurePenalty_UnseenMediaOutranksExposedMedia() {
        val meTvChannel = ChannelRegistry.get("ME_TV")!!
        val exposedItem = createMediaItem("v1", "Exposed Video")
        val unseenItem = createMediaItem("v2", "Unseen Video")
        val media = listOf(exposedItem, unseenItem)
        val context = ChannelProgrammingContext(
            availableMedia = media,
            exposureMap = mapOf("v1" to 5, "v2" to 0)
        )

        val result = programmer.programChannel(meTvChannel, context)
        assertEquals("v2", result[0].id)
    }

    @Test
    fun testSessionExposure_PenalizesActivelyPresentedPreviews() {
        val meTvChannel = ChannelRegistry.get("ME_TV")!!
        val item1 = createMediaItem("v1", "Video 1")
        val item2 = createMediaItem("v2", "Video 2")
        val media = listOf(item1, item2)

        val contextWithSessionExp = ChannelProgrammingContext(
            availableMedia = media,
            sessionExposures = mapOf("v1" to 3, "v2" to 0)
        )

        val result = programmer.programChannel(meTvChannel, contextWithSessionExp)
        assertEquals("v2", result[0].id)
    }

    @Test
    fun testRefreshEpoch_RotatesEqualCandidatesWithoutShuffling() {
        val meTvChannel = ChannelRegistry.get("ME_TV")!!
        val item1 = createMediaItem("v1", "Video 1")
        val item2 = createMediaItem("v2", "Video 2")
        val media = listOf(item1, item2)

        val contextEpoch0 = ChannelProgrammingContext(availableMedia = media, refreshEpoch = 0)
        val contextEpoch1 = ChannelProgrammingContext(availableMedia = media, refreshEpoch = 1)

        val result0 = programmer.programChannel(meTvChannel, contextEpoch0)
        val result1 = programmer.programChannel(meTvChannel, contextEpoch1)

        assertNotNull(result0)
        assertNotNull(result1)
        assertEquals(2, result0.size)
        assertEquals(2, result1.size)
    }
}
