package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPlaybackReplenishmentTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createDummyItems(count: Int, prefix: String = "library-item"): List<MediaItem> {
        return (1..count).map { i ->
            MediaItem(
                id = "$prefix-$i",
                title = "Library Item $i",
                mediaType = "VIDEO",
                year = 2024,
                duration = "01:00",
                genre = "Test",
                compatibilityStatus = CompatibilityStatus.PLAYABLE
            )
        }
    }

    @Test
    fun testLibraryPlayback_ChannelNullAuthoritativeIdentity() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val items = createDummyItems(15)
        repository.setMediaItemsForTesting(items)
        repository.setPlaylist(items.take(10), 0, "Library")

        val playlist = repository.activePlaylist.value
        assertNotNull(playlist)
        assertNull("Library playlist must have channel == null", playlist?.channel)
    }

    @Test
    fun testLowWater_TriggersLibraryReplenishment_Appends10Items() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val allItems = createDummyItems(25)
        repository.setMediaItemsForTesting(allItems)

        // Set initial library playlist of 10 items
        repository.setPlaylist(allItems.take(10), 0, "Library")
        assertEquals(10, repository.activePlaylist.value?.items?.size)

        // Select item at index 6 (4 items remaining <= 5 low-water threshold)
        repository.selectPlaylistItem(6)
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertNotNull(updatedPlaylist)
        assertEquals("Should append 10 new items on low water", 20, updatedPlaylist?.items?.size)
        assertNull("Channel must remain null", updatedPlaylist?.channel)
    }

    @Test
    fun testLibraryPlaybackReplenishment_ExcludesQueuedIDs() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val allItems = createDummyItems(25)
        repository.setMediaItemsForTesting(allItems)

        repository.setPlaylist(allItems.take(10), 0, "Library")
        repository.selectPlaylistItem(6)
        testScheduler.advanceUntilIdle()

        val items = repository.activePlaylist.value?.items ?: emptyList()
        val ids = items.map { it.id }
        assertEquals("All IDs in replenished Library playlist must be unique", ids.toSet().size, ids.size)
    }

    @Test
    fun testLibraryPlaybackReplenishment_PreservesActiveFilter() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val videos = createDummyItems(15, "vid").map { it.copy(mediaType = "VIDEO") }
        val photos = createDummyItems(10, "photo").map { it.copy(mediaType = "PHOTO") }
        val combined = videos + photos
        repository.setMediaItemsForTesting(combined)

        repository.libraryFilter = "VIDEO"
        repository.setPlaylist(videos.take(6), 0, "Library")

        repository.selectPlaylistItem(3) // 2 remaining <= 5 threshold
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertNotNull(updatedPlaylist)
        assertTrue("All replenished items must match active VIDEO filter", updatedPlaylist?.items?.all { it.mediaType == "VIDEO" } == true)
        assertNull(updatedPlaylist?.channel)
    }

    @Test
    fun testRepeatedLowWaterCallbacks_DoNotDuplicateReplenishment() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val allItems = createDummyItems(15)
        repository.setMediaItemsForTesting(allItems)

        repository.setPlaylist(allItems.take(10), 0, "Library")

        // Trigger multiple consecutive low-water calls
        repository.checkAndReplenishActivePlaylist(6, 10)
        repository.checkAndReplenishActivePlaylist(6, 10)
        testScheduler.advanceUntilIdle()

        val updatedPlaylist = repository.activePlaylist.value
        assertEquals(15, updatedPlaylist?.items?.size)
    }
}
