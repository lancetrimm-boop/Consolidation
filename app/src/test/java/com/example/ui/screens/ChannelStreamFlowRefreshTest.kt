package com.example.ui.screens

import com.example.data.Channel
import com.example.data.ChannelKind
import com.example.data.ChannelProgrammingContext
import com.example.data.ChannelProgrammer
import com.example.data.MediaItem
import org.junit.Assert.*
import org.junit.Test

class ChannelStreamFlowRefreshTest {

    @Test
    fun testAdaptiveFilter_FallsBackToAllWhenZeroVideosExist() {
        val photosOnly = listOf(
            MediaItem(id = "photo_1", title = "Photo 1", mediaType = "PHOTO", uriPath = "file://photo1.jpg"),
            MediaItem(id = "photo_2", title = "Photo 2", mediaType = "PHOTO", uriPath = "file://photo2.jpg")
        )

        val channel = Channel(
            id = "MOOD",
            title = "Mood & Aesthetic",
            query = "aesthetic",
            channelKind = ChannelKind.FIXED,
            strategyId = "MOOD"
        )

        val context = ChannelProgrammingContext(
            availableMedia = photosOnly,
            filterType = "VIDEOS"
        )

        val effectiveFilter = ChannelProgrammer.getEffectiveFilterType(channel, context)
        assertEquals("ALL", effectiveFilter)
    }

    @Test
    fun testVideoFilter_PreservedWhenVideosExist() {
        val mixedList = listOf(
            MediaItem(id = "vid_1", title = "Video 1", mediaType = "VIDEO", uriPath = "file://video1.mp4"),
            MediaItem(id = "photo_1", title = "Photo 1", mediaType = "PHOTO", uriPath = "file://photo1.jpg")
        )

        val channel = Channel(
            id = "MOOD",
            title = "Mood & Aesthetic",
            query = "aesthetic",
            channelKind = ChannelKind.FIXED,
            strategyId = "MOOD"
        )

        val context = ChannelProgrammingContext(
            availableMedia = mixedList,
            filterType = "VIDEOS"
        )

        val effectiveFilter = ChannelProgrammer.getEffectiveFilterType(channel, context)
        assertEquals("VIDEOS", effectiveFilter)
    }
}
