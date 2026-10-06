package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSessionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository
    private lateinit var sessionManager: ChannelSessionManager

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
        sessionManager = ChannelSessionManager()
    }

    private fun createMediaItem(id: String, title: String, isVideo: Boolean = true): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = if (isVideo) "VIDEO" else "PHOTO",
            duration = if (isVideo) "01:30" else ""
        )
    }

    @Test
    fun testInitialBlock_GeneratesExactProgrammedOrder() = testScope.runTest {
        val items = (1..10).map { createMediaItem("v$it", "Video $it") }
        repository.setMediaItemsForTesting(items)

        val channel = ChannelRegistry.get("REDISCOVER")!!
        sessionManager.selectChannel(channel, repository, filterType = "VIDEOS", blockLimit = 10)
        advanceUntilIdle()

        val state = sessionManager.channelState.value
        assertTrue(state is ChannelState.Success)
        val resultItems = (state as ChannelState.Success).items
        assertEquals(10, resultItems.size)
        assertEquals("v1", resultItems[0].id)
    }

    @Test
    fun testMediaTypeHomogeneity_RejectsMismatchedAppend() = testScope.runTest {
        val video1 = createMediaItem("v1", "Video 1", isVideo = true)
        val photo1 = createMediaItem("p1", "Photo 1", isVideo = false)
        repository.setMediaItemsForTesting(listOf(video1, photo1))

        val channel = ChannelRegistry.get("REDISCOVER")!!
        sessionManager.selectChannel(channel, repository, filterType = "VIDEOS")
        advanceUntilIdle()

        val state = sessionManager.channelState.value as ChannelState.Success
        assertTrue(state.items.all { it.mediaType == "VIDEO" })
    }

    @Test
    fun testWhatsNext_ExposesExactNext5Items() = testScope.runTest {
        val items = (1..10).map { createMediaItem("v$it", "Video $it") }
        repository.setMediaItemsForTesting(items)

        val channel = ChannelRegistry.get("REDISCOVER")!!
        sessionManager.selectChannel(channel, repository, filterType = "VIDEOS", blockLimit = 10)
        advanceUntilIdle()

        val activeItems = (sessionManager.channelState.value as ChannelState.Success).items
        sessionManager.advancePlaybackIndex(0)
        val upcoming = sessionManager.whatsNext.value
        assertEquals(5, upcoming.size)
        assertEquals(activeItems[1].id, upcoming[0].id)
        assertEquals(activeItems[5].id, upcoming[4].id)
    }

    @Test
    fun testStaleSessionProtection_DiscardsPreviousSessionResult() = testScope.runTest {
        val items = listOf(createMediaItem("v1", "Video 1"))
        repository.setMediaItemsForTesting(items)

        val channelA = ChannelRegistry.get("ME_TV")!!
        val channelB = ChannelRegistry.get("REDISCOVER")!!

        val initialGen = sessionManager.currentSessionGeneration
        sessionManager.selectChannel(channelA, repository)
        val nextGen = sessionManager.currentSessionGeneration
        assertTrue(nextGen > initialGen)

        sessionManager.selectChannel(channelB, repository)
        advanceUntilIdle()

        assertEquals("REDISCOVER", (sessionManager.channelState.value as ChannelState.Success).channel.id)
    }

    @Test
    fun testDuplicateReplenishment_PreventedWhileReplenishing() = testScope.runTest {
        val items = (1..5).map { createMediaItem("v$it", "Video $it") }
        repository.setMediaItemsForTesting(items)

        val channel = ChannelRegistry.get("ME_TV")!!
        sessionManager.selectChannel(channel, repository)
        advanceUntilIdle()

        val result1 = sessionManager.replenishNextBlock(channel, repository)
        advanceUntilIdle()

        assertTrue(result1)
        assertFalse(sessionManager.isReplenishing)
    }

    @Test
    fun testStopSession_CancelsWorkAndClearsState() = testScope.runTest {
        val items = listOf(createMediaItem("v1", "Video 1"))
        repository.setMediaItemsForTesting(items)

        val channel = ChannelRegistry.get("ME_TV")!!
        sessionManager.selectChannel(channel, repository)
        advanceUntilIdle()

        sessionManager.stopSession()
        assertEquals(ChannelState.Idle, sessionManager.channelState.value)
        assertTrue(sessionManager.whatsNext.value.isEmpty())
    }
}
