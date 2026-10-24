package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExhaustionTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createDummyItems(count: Int, prefix: String = "ex-item"): List<MediaItem> {
        return (1..count).map { i ->
            MediaItem(
                id = "$prefix-$i",
                title = "Item $i",
                mediaType = "VIDEO",
                year = 2024,
                duration = "01:00",
                genre = "Test",
                compatibilityStatus = CompatibilityStatus.PLAYABLE
            )
        }
    }

    @Test
    fun testExhaustion_ReplenishmentReturnsZero_WhenNoEligibleMediaRemains() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val items = createDummyItems(8)
        repository.setMediaItemsForTesting(items)

        // Library playlist contains all 8 items
        repository.setPlaylist(items, 0, "Library")
        assertEquals(8, repository.activePlaylist.value?.items?.size)

        // Select item near end (index 6, 1 remaining <= 5 threshold)
        repository.selectPlaylistItem(6)
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertEquals("When no new media remains, size remains 8", 8, updatedPlaylist?.items?.size)
    }

    @Test
    fun testLibraryPlayback_Exhaustion_ChannelRemainsNull() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val items = createDummyItems(5)
        repository.setMediaItemsForTesting(items)

        repository.setPlaylist(items, 0, "Library")
        repository.selectPlaylistItem(3)
        testScheduler.advanceUntilIdle()

        val playlist = repository.activePlaylist.value
        assertNotNull(playlist)
        assertNull("Channel must remain null for Library playback even at exhaustion", playlist?.channel)
    }

    @Test
    fun testChannelPlayback_Exhaustion_PreservesChannelIdentity() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val items = createDummyItems(5)
        repository.setMediaItemsForTesting(items)

        val channel = ChannelRegistry.allFixedChannels().first()
        repository.setChannelPlaylist(channel, "ALL", items, 0, channel.title)

        repository.selectPlaylistItem(3)
        testScheduler.advanceUntilIdle()

        val playlist = repository.activePlaylist.value
        assertNotNull(playlist)
        assertEquals("Me TV", playlist?.channel?.title)
    }
}
