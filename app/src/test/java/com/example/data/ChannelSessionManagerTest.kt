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
class ChannelSessionManagerTest {

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

    private fun createMediaItem(id: String, title: String): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "PHOTO",
            year = 2024,
            duration = "",
            genre = "Media",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testChannelModel_Creation() {
        val channel = Channel(
            id = "vibrant_cinema",
            title = "Vibrant Cinema",
            query = "Cinematic vibrant dramatic lighting",
            referenceMediaIds = listOf("ref1", "ref2")
        )

        assertEquals("vibrant_cinema", channel.id)
        assertEquals("Vibrant Cinema", channel.title)
        assertEquals("Cinematic vibrant dramatic lighting", channel.query)
        assertEquals(2, channel.referenceMediaIds.size)
    }

    @Test
    fun testChannelSessionManager_SelectChannel_SuccessState() = runTest(testDispatcher) {
        val channel = Channel(id = "cinema", title = "Cinema", query = "Cinematic lighting")
        val item1 = createMediaItem("1", "Movie Scene 1")
        val item2 = createMediaItem("2", "Movie Scene 2")

        val candidates = listOf(
            IntelligenceCandidate(item = item1, evidence = emptyList(), rankScore = 0.9, primaryRelevanceScore = 0.9f, secondaryEvidenceScore = 0.0f),
            IntelligenceCandidate(item = item2, evidence = emptyList(), rankScore = 0.8, primaryRelevanceScore = 0.8f, secondaryEvidenceScore = 0.0f)
        )
        val response = IntelligenceResponse(requestId = "test_req", mode = IntelligenceMode.SEARCH, candidates = candidates, latencyMs = 10L)

        whenever(mockCore.processRequest(any())).thenReturn(response)

        sessionManager.selectChannel(channel, repository)
        advanceUntilIdle()

        val state = sessionManager.channelState.value
        assertTrue(state is ChannelState.Success)
        val success = state as ChannelState.Success
        assertEquals("cinema", success.channel.id)
        assertEquals(2, success.items.size)
        assertEquals("1", success.items[0].id)
        assertEquals("2", success.items[1].id)

        // Verify processRequest received correct mode and query
        argumentCaptor<IntelligenceRequest>().apply {
            verify(mockCore).processRequest(capture())
            assertEquals(IntelligenceMode.SEARCH, firstValue.mode)
            assertEquals("Cinematic lighting", firstValue.query)
            assertTrue(firstValue.skipPersistence)
        }
    }

    @Test
    fun testChannelSessionManager_SwitchChannels_UpdatesState() = runTest(testDispatcher) {
        val channelA = Channel(id = "ch_A", title = "Channel A", query = "Query A")
        val channelB = Channel(id = "ch_B", title = "Channel B", query = "Query B")
        val itemA = createMediaItem("a", "Item A")
        val itemB = createMediaItem("b", "Item B")

        val respA = IntelligenceResponse(
            requestId = "req_A", mode = IntelligenceMode.SEARCH,
            candidates = listOf(IntelligenceCandidate(itemA, emptyList(), 0.9, 0.9f, 0f)),
            latencyMs = 10L
        )
        val respB = IntelligenceResponse(
            requestId = "req_B", mode = IntelligenceMode.SEARCH,
            candidates = listOf(IntelligenceCandidate(itemB, emptyList(), 0.8, 0.8f, 0f)),
            latencyMs = 10L
        )

        whenever(mockCore.processRequest(argThat { query == "Query A" })).thenReturn(respA)
        whenever(mockCore.processRequest(argThat { query == "Query B" })).thenReturn(respB)

        // Select A
        sessionManager.selectChannel(channelA, repository)
        advanceUntilIdle()

        val stateA = sessionManager.channelState.value as ChannelState.Success
        assertEquals("ch_A", stateA.channel.id)
        assertEquals("a", stateA.items[0].id)

        // Select B
        sessionManager.selectChannel(channelB, repository)
        advanceUntilIdle()

        val stateB = sessionManager.channelState.value as ChannelState.Success
        assertEquals("ch_B", stateB.channel.id)
        assertEquals("b", stateB.items[0].id)
    }

    @Test
    fun testChannelSessionManager_NullEngine_ErrorState() = runTest(testDispatcher) {
        repository.setIntelligenceCoreForTesting(null)
        val channel = Channel(id = "ch_err", title = "Error Channel", query = "Query")

        sessionManager.selectChannel(channel, repository)
        advanceUntilIdle()

        val state = sessionManager.channelState.value
        assertTrue(state is ChannelState.Error)
        val error = state as ChannelState.Error
        assertEquals("ch_err", error.channel.id)
        assertTrue(error.message.contains("not ready"))
    }
}
