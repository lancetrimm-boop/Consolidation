package com.example.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchSeededHomogeneityTest {

    private lateinit var programmer: ChannelProgrammer

    private val video1 = MediaItem(id = "vid1", title = "Ocean Waves Video", mediaType = "VIDEO", uriPath = "v1")
    private val video2 = MediaItem(id = "vid2", title = "Mountain Sunset Video", mediaType = "VIDEO", uriPath = "v2")
    private val photo1 = MediaItem(id = "pic1", title = "Ocean Waves Photo", mediaType = "PHOTO", uriPath = "p1")
    private val photo2 = MediaItem(id = "pic2", title = "Mountain Sunset Photo", mediaType = "PHOTO", uriPath = "p2")

    private val availableMedia = listOf(video1, video2, photo1, photo2)

    @Before
    fun setUp() {
        programmer = ChannelProgrammer()
    }

    @Test
    fun testA_VideoSeed_OverridesPhotosFilter_ReturnsVideosOnly() {
        val channel = Channel(
            id = "channel_search_video_seed",
            title = "Video Seed Channel",
            referenceMediaIds = listOf("vid1"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        val context = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "PHOTOS" // UI filter set to Photos
        )

        val programmed = programmer.programChannel(channel, context, limit = 20)

        // Assert: All programmed items are videos despite filterType = "PHOTOS"
        assertTrue("Programmed list should not be empty", programmed.isNotEmpty())
        assertTrue("All items must be videos", programmed.all { it.mediaType == "VIDEO" })
        assertTrue("Must contain the first reference vid1", programmed.any { it.id == "vid1" })
    }

    @Test
    fun testB_PhotoSeed_OverridesVideosFilter_ReturnsPhotosOnly() {
        val channel = Channel(
            id = "channel_search_photo_seed",
            title = "Photo Seed Channel",
            referenceMediaIds = listOf("pic1"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        val context = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "VIDEOS" // UI filter set to Videos
        )

        val programmed = programmer.programChannel(channel, context, limit = 20)

        // Assert: All programmed items are photos despite filterType = "VIDEOS"
        assertTrue("Programmed list should not be empty", programmed.isNotEmpty())
        assertTrue("All items must be photos", programmed.all { it.mediaType == "PHOTO" })
        assertTrue("Must contain the first reference pic1", programmed.any { it.id == "pic1" })
    }

    @Test
    fun testC_MatchingFilter_MaintainsNormalBehavior() {
        val videoChannel = Channel(
            id = "channel_search_vid",
            title = "Video Channel",
            referenceMediaIds = listOf("vid1"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        val videoContext = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "VIDEOS"
        )

        val videoProgrammed = programmer.programChannel(videoChannel, videoContext, limit = 20)
        assertTrue(videoProgrammed.all { it.mediaType == "VIDEO" })

        val photoChannel = Channel(
            id = "channel_search_pic",
            title = "Photo Channel",
            referenceMediaIds = listOf("pic1"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        val photoContext = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "PHOTOS"
        )

        val photoProgrammed = programmer.programChannel(photoChannel, photoContext, limit = 20)
        assertTrue(photoProgrammed.all { it.mediaType == "PHOTO" })
    }

    @Test
    fun testD_InvalidReference_FallsBackToContextFilter() {
        val channel = Channel(
            id = "channel_search_invalid_seed",
            title = "Invalid Seed Channel",
            referenceMediaIds = listOf("non_existent_id"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        val photosContext = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "PHOTOS"
        )
        val photosProgrammed = programmer.programChannel(channel, photosContext, limit = 20)
        assertTrue(photosProgrammed.all { it.mediaType == "PHOTO" })

        val videosContext = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "VIDEOS"
        )
        val videosProgrammed = programmer.programChannel(channel, videosContext, limit = 20)
        assertTrue(videosProgrammed.all { it.mediaType == "VIDEO" })
    }

    @Test
    fun testE_BuiltInFixedChannel_RespectsContextFilter_UnaffectedByOverride() {
        val fixedChannel = Channel(
            id = "FAVORITES",
            title = "Favorites Stream",
            referenceMediaIds = listOf("vid1"), // Even if reference ID is attached
            channelKind = ChannelKind.FIXED,
            strategyId = "FAVORITES"
        )

        val photosContext = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "PHOTOS"
        )
        val photosProgrammed = programmer.programChannel(fixedChannel, photosContext, limit = 20)
        assertTrue("Fixed channels must respect context.filterType (PHOTOS)", photosProgrammed.all { it.mediaType == "PHOTO" })

        val videosContext = ChannelProgrammingContext(
            availableMedia = availableMedia,
            filterType = "VIDEOS"
        )
        val videosProgrammed = programmer.programChannel(fixedChannel, videosContext, limit = 20)
        assertTrue("Fixed channels must respect context.filterType (VIDEOS)", videosProgrammed.all { it.mediaType == "VIDEO" })
    }
}
