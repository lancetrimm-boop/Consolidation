package com.example.data

import com.example.data.intelligence.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelPlaybackTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MediaRepository
    private lateinit var sessionManager: ChannelSessionManager
    private val mockCore: AuraIntelligenceCore = mock()

    @Before
    fun setUp() {
        com.example.data.intelligence.IntelligenceCache.clear()
        repository = MediaRepository(testDispatcher)
        repository.setIntelligenceCoreForTesting(mockCore)
        sessionManager = ChannelSessionManager()
    }

    private fun createMediaItem(id: String, title: String, isVideo: Boolean): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = if (isVideo) "VIDEO" else "PHOTO",
            year = 2024,
            duration = if (isVideo) "01:30" else "",
            genre = "Media",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testPhotosFilter_PassesFilterTypePhotosToCore() = runTest(testDispatcher) {
        val channel = Channel(id = "cinema", title = "Vibrant Cinema", query = "Cinematic lighting")
        val photo1 = createMediaItem("p1", "Photo 1", isVideo = false)
        val photo2 = createMediaItem("p2", "Photo 2", isVideo = false)

        val candidates = listOf(
            IntelligenceCandidate(photo1, emptyList(), 0.9, 0.9f, 0f),
            IntelligenceCandidate(photo2, emptyList(), 0.8, 0.8f, 0f)
        )
        val response = IntelligenceResponse("req1", IntelligenceMode.SEARCH, candidates, latencyMs = 10L)

        whenever(mockCore.processRequest(any())).thenReturn(response)

        sessionManager.selectChannel(channel, repository, filterType = "PHOTOS")
        advanceUntilIdle()

        argumentCaptor<IntelligenceRequest>().apply {
            verify(mockCore).processRequest(capture())
            assertEquals("PHOTOS", firstValue.filterType)
            assertEquals(IntelligenceMode.SEARCH, firstValue.mode)
        }

        val state = sessionManager.channelState.value as ChannelState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.all { it.mediaType == "PHOTO" })
    }

    @Test
    fun testVideosFilter_PassesFilterTypeVideosToCore() = runTest(testDispatcher) {
        val channel = Channel(id = "action", title = "Action & Motion", query = "High motion")
        val video1 = createMediaItem("v1", "Video 1", isVideo = true)
        val video2 = createMediaItem("v2", "Video 2", isVideo = true)

        val candidates = listOf(
            IntelligenceCandidate(video1, emptyList(), 0.95, 0.95f, 0f),
            IntelligenceCandidate(video2, emptyList(), 0.85, 0.85f, 0f)
        )
        val response = IntelligenceResponse("req2", IntelligenceMode.SEARCH, candidates, latencyMs = 10L)

        whenever(mockCore.processRequest(any())).thenReturn(response)

        sessionManager.selectChannel(channel, repository, filterType = "VIDEOS")
        advanceUntilIdle()

        argumentCaptor<IntelligenceRequest>().apply {
            verify(mockCore).processRequest(capture())
            assertEquals("VIDEOS", firstValue.filterType)
            assertEquals(IntelligenceMode.SEARCH, firstValue.mode)
        }

        val state = sessionManager.channelState.value as ChannelState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.all { it.mediaType == "VIDEO" })
    }

    @Test
    fun testRankingPreservation_SetPlaylistPreservesAuraOrder() {
        val v1 = createMediaItem("v1", "Rank 1 Video", isVideo = true)
        val v3 = createMediaItem("v3", "Rank 2 Video", isVideo = true)
        val v2 = createMediaItem("v2", "Rank 3 Video", isVideo = true)
        val rankedList = listOf(v1, v3, v2)

        repository.setPlaylist(rankedList, initialIndex = 0, sourceTitle = "Channel — Test")

        val active = repository.activePlaylist.value
        assertNotNull(active)
        assertEquals(3, active!!.items.size)
        assertEquals("v1", active.items[0].id)
        assertEquals("v3", active.items[1].id)
        assertEquals("v2", active.items[2].id)
    }

    @Test
    fun testSelectedIndex_StartsAtIndexAndPreservesRemainingSequence() {
        val v1 = createMediaItem("v1", "Video 1", isVideo = true)
        val v2 = createMediaItem("v2", "Video 2", isVideo = true)
        val v3 = createMediaItem("v3", "Video 3", isVideo = true)
        val v4 = createMediaItem("v4", "Video 4", isVideo = true)
        val rankedList = listOf(v1, v2, v3, v4)

        // Select V3 (index 2)
        repository.setPlaylist(rankedList, initialIndex = 2, sourceTitle = "Channel — Test")

        val active = repository.activePlaylist.value
        assertNotNull(active)
        assertEquals(2, active!!.currentIndex)
        assertEquals("v3", active.currentItem?.id)

        // Advance Next -> V4
        repository.nextPlaylistItem()
        val afterNext = repository.activePlaylist.value
        assertEquals(3, afterNext!!.currentIndex)
        assertEquals("v4", afterNext.currentItem?.id)
    }

    @Test
    fun testChannelSwitching_ReplacesActivePlaylist() {
        val channelAItems = listOf(createMediaItem("a1", "A1", isVideo = true), createMediaItem("a2", "A2", isVideo = true))
        val channelBItems = listOf(createMediaItem("b1", "B1", isVideo = true), createMediaItem("b2", "B2", isVideo = true))

        // Start Channel A playback
        repository.setPlaylist(channelAItems, initialIndex = 1, sourceTitle = "Channel A")
        assertEquals("a2", repository.activePlaylist.value?.currentItem?.id)

        // Switch to Channel B playback
        repository.setPlaylist(channelBItems, initialIndex = 0, sourceTitle = "Channel B")
        val activeB = repository.activePlaylist.value
        assertEquals("Channel B", activeB?.sourceTitle)
        assertEquals("b1", activeB?.currentItem?.id)
        assertFalse(activeB!!.items.any { it.id.startsWith("a") })
    }
}
