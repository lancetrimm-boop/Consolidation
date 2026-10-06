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
class FavoriteSynchronizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createMediaItem(id: String, title: String, isFavorite: Boolean = false): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "VIDEO",
            duration = "01:00",
            genre = "Test",
            isFavorite = isFavorite
        )
    }

    @Test
    fun testToggleFavorite_NonFavoriteToFavorite_UpdatesAllRepresentations() = testScope.runTest {
        val item = createMediaItem("m1", "Media 1", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))
        repository.setLibraryPlaylist(listOf(item), 0)
        advanceUntilIdle()

        // Verify initial non-favorite state
        assertFalse(repository.getMediaItemById("m1")!!.isFavorite)
        assertFalse(repository.mediaItemsMap.value["m1"]!!.isFavorite)
        assertFalse(repository.activePlaylist.value!!.items[0].isFavorite)

        // Toggle Favorite -> ON
        repository.toggleFavorite("m1")
        advanceUntilIdle()

        // Verify synchronous state across all 3 representations
        assertTrue("Repository item must be favorite", repository.getMediaItemById("m1")!!.isFavorite)
        assertTrue("mediaItemsMap item must be favorite", repository.mediaItemsMap.value["m1"]!!.isFavorite)
        assertTrue("Active playlist item must be favorite", repository.activePlaylist.value!!.items[0].isFavorite)
    }

    @Test
    fun testToggleFavorite_FavoriteToNonFavorite_UpdatesActivePlaylist() = testScope.runTest {
        val item = createMediaItem("m2", "Media 2", isFavorite = true)
        repository.setMediaItemsForTesting(listOf(item))
        repository.setLibraryPlaylist(listOf(item), 0)
        advanceUntilIdle()

        assertTrue(repository.activePlaylist.value!!.items[0].isFavorite)

        // Toggle Favorite -> OFF
        repository.toggleFavorite("m2")
        advanceUntilIdle()

        assertFalse("Repository item must be non-favorite", repository.getMediaItemById("m2")!!.isFavorite)
        assertFalse("mediaItemsMap item must be non-favorite", repository.mediaItemsMap.value["m2"]!!.isFavorite)
        assertFalse("Active playlist item must be non-favorite", repository.activePlaylist.value!!.items[0].isFavorite)
    }

    @Test
    fun testToggleFavorite_MultiplePlaylistItems_OnlyTargetItemChanges() = testScope.runTest {
        val itemA = createMediaItem("A", "Item A", isFavorite = false)
        val itemB = createMediaItem("B", "Item B", isFavorite = false)
        val itemC = createMediaItem("C", "Item C", isFavorite = false)

        val items = listOf(itemA, itemB, itemC)
        repository.setMediaItemsForTesting(items)
        repository.setLibraryPlaylist(items, 1) // Active index = 1 (B)
        advanceUntilIdle()

        repository.toggleFavorite("B")
        advanceUntilIdle()

        val playlistItems = repository.activePlaylist.value!!.items
        assertFalse("Item A in playlist should remain unchanged", playlistItems[0].isFavorite)
        assertTrue("Item B in playlist should be favorite", playlistItems[1].isFavorite)
        assertFalse("Item C in playlist should remain unchanged", playlistItems[2].isFavorite)
    }

    @Test
    fun testToggleFavorite_NoActivePlaylist_DoesNotCrash() = testScope.runTest {
        val item = createMediaItem("m3", "Media 3", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        assertNull("Active playlist should be null", repository.activePlaylist.value)

        // Toggle favorite without an active playlist
        repository.toggleFavorite("m3")
        advanceUntilIdle()

        assertTrue(repository.getMediaItemById("m3")!!.isFavorite)
        assertNull("Active playlist remains null without crashing", repository.activePlaylist.value)
    }

    @Test
    fun testAddToFavoritesAndRemoveFromFavorites_UpdatesActivePlaylist() = testScope.runTest {
        val item = createMediaItem("m4", "Media 4", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))
        repository.setLibraryPlaylist(listOf(item), 0)
        advanceUntilIdle()

        repository.addToFavorites("m4")
        advanceUntilIdle()
        assertTrue(repository.activePlaylist.value!!.items[0].isFavorite)

        repository.removeFromFavorites("m4")
        advanceUntilIdle()
        assertFalse(repository.activePlaylist.value!!.items[0].isFavorite)
    }
}
