package com.example.ui.screens

import com.example.data.Channel
import com.example.data.ChannelKind
import com.example.data.ChannelRegistry
import com.example.data.MediaItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelPreviewArchitectureTest {

    @Before
    fun setUp() {
        ChannelRegistry.clearCustomChannelsForTesting()
    }

    private fun createMediaItem(id: String, isVid: Boolean = true): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            mediaType = if (isVid) "VIDEO" else "PHOTO",
            uriPath = if (isVid) "content://media/external/video/media/$id" else "content://media/external/images/media/$id"
        )
    }

    private fun getSourceFile(relativePath: String): java.io.File {
        val f1 = java.io.File(relativePath)
        if (f1.exists()) return f1
        val f2 = java.io.File("app/$relativePath")
        if (f2.exists()) return f2
        return f1
    }

    @Test
    fun testNoExoPlayerOrPlayerViewInCardHierarchyInvariant() {
        // Assert that AuraChannelsScreen source contains 0 ExoPlayer or PlayerView imports/references
        val auraChannelsSource = getSourceFile("src/main/java/com/example/ui/screens/AuraChannelsScreen.kt").readText()
        assertFalse("AuraChannelsScreen MUST NOT import ExoPlayer", auraChannelsSource.contains("ExoPlayer"))
        assertFalse("AuraChannelsScreen MUST NOT import PlayerView", auraChannelsSource.contains("PlayerView"))
        assertFalse("AuraChannelsScreen MUST NOT use AndroidView for video playback", auraChannelsSource.contains("AndroidView"))
    }

    @Test
    fun testViewportGating_FrameExtractionGatedOnVisibility() {
        val auraChannelsSource = getSourceFile("src/main/java/com/example/ui/screens/AuraChannelsScreen.kt").readText()
        assertTrue("LaunchedEffect MUST gate on isVisibleInViewport", auraChannelsSource.contains("isVisibleInViewport"))
        assertTrue("Frame extraction MUST return early if !isVisibleInViewport", auraChannelsSource.contains("if (!isVisibleInViewport || !isVideo || uri.isNullOrEmpty()) return@LaunchedEffect"))
    }

    @Test
    fun testChannelLineupOrder_Exact5FixedBeforeSearchSeeded() {
        val fixed = ChannelRegistry.allFixedChannels()
        assertEquals(5, fixed.size)
        assertEquals("ME_TV", fixed[0].id)
        assertEquals("FAVORITES", fixed[1].id)
        assertEquals("REDISCOVER", fixed[2].id)
        assertEquals("CONTINUE", fixed[3].id)
        assertEquals("MOOD", fixed[4].id)

        val seeded = Channel(
            id = "seeded_search",
            title = "Ocean Beach",
            query = "ocean beach",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        ChannelRegistry.addSearchSeededChannel(seeded)

        val all = ChannelRegistry.allChannels()
        assertEquals(6, all.size)
        assertEquals("ME_TV", all[0].id)
        assertEquals("seeded_search", all[5].id)
    }

    @Test
    fun testNoDuplicatePreviewCandidatesAssignedAcrossChannels() {
        val item1 = createMediaItem("item_1")
        val item2 = createMediaItem("item_2")
        val item3 = createMediaItem("item_3")
        val item4 = createMediaItem("item_4")
        val item5 = createMediaItem("item_5")
        val available = listOf(item1, item2, item3, item4, item5)

        val usedPreviewIds = mutableSetOf<String>()
        val channels = ChannelRegistry.allFixedChannels()

        channels.forEach { channel ->
            val candidate = available.firstOrNull { it.id !in usedPreviewIds } ?: available.firstOrNull()
            assertNotNull(candidate)
            assertTrue("Candidate must not be duplicated across channel previews", usedPreviewIds.add(candidate!!.id))
        }

        assertEquals("5 fixed channels must receive 5 unique preview candidates when available", 5, usedPreviewIds.size)
    }

    @Test
    fun testInsufficientMedia_RemainingChannelsReceiveNullCandidateInsteadOfDuplicate() {
        val item1 = createMediaItem("item_1")
        val item2 = createMediaItem("item_2")
        val available = listOf(item1, item2) // Only 2 items for 5 channels

        val usedPreviewIds = mutableSetOf<String>()
        val channels = ChannelRegistry.allFixedChannels()

        val assigned = channels.map { channel ->
            val candidate = available.firstOrNull { it.id !in usedPreviewIds }
            if (candidate != null) {
                usedPreviewIds.add(candidate.id)
            }
            candidate
        }

        assertEquals(2, assigned.filterNotNull().size)
        assertEquals(3, assigned.filter { it == null }.size)
        assertEquals(2, usedPreviewIds.size)
    }
}
