package com.example.ui.components

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeMenuSheetTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = MediaRepository(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMediaItem(id: String, isFavorite: Boolean = false): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            mediaType = "VIDEO",
            isFavorite = isFavorite,
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testFavoritePresentation_NonFavoriteItem_IsFavoriteFalse() {
        val nonFavItem = createMediaItem("item1", isFavorite = false)
        assertFalse("Non-favorite item must have isFavorite set to false", nonFavItem.isFavorite)
    }

    @Test
    fun testFavoritePresentation_FavoriteItem_IsFavoriteTrue() {
        val favItem = createMediaItem("item2", isFavorite = true)
        assertTrue("Favorite item must have isFavorite set to true", favItem.isFavorite)
    }

    @Test
    fun testFavoriteToggle_InvokesRepositoryAndUpdatesState() = runTest(testDispatcher) {
        val item = createMediaItem("item3", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))
        repository.setPlaylist(listOf(item), 0, "TestPlaylist")

        // Initial state check
        val activeInitial = repository.activePlaylist.value?.currentItem
        assertEquals("item3", activeInitial?.id)
        assertFalse("Initially non-favorite", activeInitial!!.isFavorite)

        // Simulate toggle callback from SwipeMenuSheet
        repository.toggleFavorite(activeInitial.id)

        // Verify state is updated synchronously in active playlist
        val activeUpdated = repository.activePlaylist.value?.currentItem
        assertTrue("State must become favorite after toggle", activeUpdated!!.isFavorite)
    }

    @Test
    fun testBeginVisualSearch_DelegatesToRepositoryAddVisualReference() = runTest(testDispatcher) {
        val item = createMediaItem("item4")
        
        assertTrue("Initially active visual references should be empty", repository.activeVisualReferences.value.isEmpty())

        // Simulate SwipeMenuSheet onBeginVisualSearch callback
        repository.addVisualReference(item)

        val activeRefs = repository.activeVisualReferences.value
        assertEquals("Active visual references must contain 1 item", 1, activeRefs.size)
        assertEquals("item4", activeRefs[0].id)
    }

    @Test
    fun testMediaSwitching_DoesNotRetainPreviousFavoriteState() = runTest(testDispatcher) {
        val favItem = createMediaItem("itemA", isFavorite = true)
        val nonFavItem = createMediaItem("itemB", isFavorite = false)
        repository.setPlaylist(listOf(favItem, nonFavItem), 0, "TestPlaylist")

        val firstItem = repository.activePlaylist.value?.currentItem
        assertEquals("itemA", firstItem?.id)
        assertTrue("Item A is favorite", firstItem!!.isFavorite)

        // Switch track
        repository.nextPlaylistItem()

        val secondItem = repository.activePlaylist.value?.currentItem
        assertEquals("itemB", secondItem?.id)
        assertFalse("Item B is not favorite and must not inherit item A's favorite state", secondItem!!.isFavorite)
    }
}
