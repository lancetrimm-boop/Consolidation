package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelContinuousReplenishmentTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createDummyItems(count: Int, prefix: String = "chan-item"): List<MediaItem> {
        return (1..count).map { i ->
            MediaItem(
                id = "$prefix-$i",
                title = "Channel Item $i",
                mediaType = "VIDEO",
                year = 2024,
                duration = "01:00",
                genre = "Test",
                compatibilityStatus = CompatibilityStatus.PLAYABLE
            )
        }
    }

    @Test
    fun testChannelReplenishment_InitialTarget20_LowWaterAppends10() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val allItems = createDummyItems(35)
        repository.setMediaItemsForTesting(allItems)

        val channel = ChannelRegistry.allFixedChannels().first() // e.g. Me TV
        repository.setChannelPlaylist(channel, "ALL", allItems.take(20), 0, channel.title)

        val initialPlaylist = repository.activePlaylist.value
        assertEquals(20, initialPlaylist?.items?.size)
        assertNotNull(initialPlaylist?.channel)

        // Select item at index 16 (3 items remaining <= 5 low-water threshold)
        repository.selectPlaylistItem(16)
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertEquals(30, updatedPlaylist?.items?.size)
        assertEquals("Me TV", updatedPlaylist?.channel?.title)
    }

    @Test
    fun testChannelReplenishment_ExcludesAlreadyQueuedIDs() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val allItems = createDummyItems(35)
        repository.setMediaItemsForTesting(allItems)

        val channel = ChannelRegistry.allFixedChannels().first()
        repository.setChannelPlaylist(channel, "ALL", allItems.take(20), 0, channel.title)

        repository.selectPlaylistItem(16)
        testScheduler.advanceUntilIdle()

        val items = repository.activePlaylist.value?.items ?: emptyList()
        val ids = items.map { it.id }
        assertEquals("All items in replenished Channel playlist must be unique", ids.toSet().size, ids.size)
    }

    @Test
    fun testChannelReplenishment_PreservesStrategyAndFilter() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val allItems = createDummyItems(35)
        repository.setMediaItemsForTesting(allItems)

        val channel = ChannelRegistry.allFixedChannels().first()
        repository.setChannelPlaylist(channel, "VIDEO", allItems.take(20), 0, channel.title)

        repository.selectPlaylistItem(16)
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertEquals("Me TV", updatedPlaylist?.channel?.title)
        assertEquals("VIDEO", updatedPlaylist?.channelFilterType)
    }

    @Test
    fun testSearchSeededChannelReplenishment_PreservesReferenceAndQuery() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val videos = createDummyItems(35, "search-vid")
        repository.setMediaItemsForTesting(videos)

        val searchSeeded = Channel(
            id = "channel_search_999",
            title = "Visual Search",
            query = "test_query",
            referenceMediaIds = listOf("search-vid-1"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        repository.setChannelPlaylist(searchSeeded, "VIDEO", videos.take(20), 0, searchSeeded.title)

        repository.selectPlaylistItem(16)
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertEquals("channel_search_999", updatedPlaylist?.channel?.id)
        assertEquals("test_query", updatedPlaylist?.channel?.query)
        assertEquals(listOf("search-vid-1"), updatedPlaylist?.channel?.referenceMediaIds)
    }
}
