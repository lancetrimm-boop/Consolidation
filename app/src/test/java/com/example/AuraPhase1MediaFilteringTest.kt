package com.example

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.StandardSortOption
import com.example.data.IntelligentSortOption
import com.example.data.SortCategory
import com.example.data.TasteDNA
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuraPhase1MediaFilteringTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        repository = MediaRepository(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::repository.isInitialized) {
            repository.close()
        }
    }

    private fun createMediaItem(id: String, status: CompatibilityStatus, isDeleted: Boolean = false): MediaItem {
        return MediaItem(
            id = id,
            title = "Media $id",
            mediaType = "VIDEO",
            compatibilityStatus = status,
            isDeleted = isDeleted
        )
    }

    @Test
    fun testMediaValidityFiltering() = runTest(testDispatcher) {
        try {
            val items = listOf(
                createMediaItem("1", CompatibilityStatus.PLAYABLE),
                createMediaItem("2", CompatibilityStatus.CORRUPT),
                createMediaItem("3", CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE),
                createMediaItem("4", CompatibilityStatus.DELETED),
                createMediaItem("5", CompatibilityStatus.UNTESTED),
                createMediaItem("6", CompatibilityStatus.THUMBNAIL_FAILED),
                createMediaItem("7", CompatibilityStatus.PLAYABLE, isDeleted = true)
            )

            val filtered = repository.getFilteredAndSortedMedia(
                filterType = "ALL",
                sortCategory = SortCategory.STANDARD,
                standardSort = StandardSortOption.NEWEST_FIRST,
                intelligentSort = IntelligentSortOption.PERSONALIZED,
                inputItems = items,
                tasteDNA = TasteDNA()
            )

            // Should preserve in Library: 1 (PLAYABLE), 3 (PLAYABLE_SOFTWARE_DECODE), 6 (THUMBNAIL_FAILED)
            assertEquals(3, filtered.size)
            assertTrue(filtered.any { it.id == "1" })
            assertTrue(filtered.any { it.id == "3" })
            assertTrue(filtered.any { it.id == "6" })
            assertFalse(filtered.any { it.id == "2" })
            assertFalse(filtered.any { it.id == "4" })
            assertFalse(filtered.any { it.id == "5" })
            assertFalse(filtered.any { it.id == "7" })
        } finally {
            repository.close()
        }
    }

    @Test
    fun testPlaylistSanitization() = runTest(testDispatcher) {
        try {
            val items = listOf(
                createMediaItem("1", CompatibilityStatus.PLAYABLE),
                createMediaItem("2", CompatibilityStatus.CORRUPT), // Should be removed
                createMediaItem("3", CompatibilityStatus.PLAYABLE)
            )

            // Start at item "3" (index 2)
            repository.setPlaylist(items, 2, "Test Playlist")

            val playlist = repository.activePlaylist.value
            assertNotNull(playlist)
            assertEquals(2, playlist?.items?.size) // Item "2" removed
            assertEquals("1", playlist?.items?.get(0)?.id)
            assertEquals("3", playlist?.items?.get(1)?.id)
            
            // Index should be corrected from 2 to 1 because item "2" was removed
            assertEquals(1, playlist?.currentIndex)
        } finally {
            repository.close()
        }
    }

    @Test
    fun testPlaylistSanitizationSelectionFilteredOut() = runTest(testDispatcher) {
        try {
            val items = listOf(
                createMediaItem("1", CompatibilityStatus.PLAYABLE),
                createMediaItem("2", CompatibilityStatus.CORRUPT), // Selected but filtered out
                createMediaItem("3", CompatibilityStatus.PLAYABLE)
            )

            // Start at item "2" (index 1) which is invalid
            repository.setPlaylist(items, 1, "Test Playlist")

            val playlist = repository.activePlaylist.value
            assertNotNull(playlist)
            assertEquals(2, playlist?.items?.size)
            // Selected item "2" was filtered out, should default to 0
            assertEquals(0, playlist?.currentIndex)
            assertEquals("1", playlist?.items?.get(0)?.id)
        } finally {
            repository.close()
        }
    }

    @Test
    fun testPhotoAndVideoFilterSeparation() = runTest(testDispatcher) {
        try {
            val photoItem = MediaItem(id = "p1", title = "Photo 1", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
            val videoItem = MediaItem(id = "v1", title = "Video 1", mediaType = "VIDEO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
            val items = listOf(photoItem, videoItem)

            val photoFilterResults = repository.getFilteredAndSortedMedia(
                filterType = "PHOTO",
                sortCategory = SortCategory.STANDARD,
                standardSort = StandardSortOption.NEWEST_FIRST,
                intelligentSort = IntelligentSortOption.PERSONALIZED,
                inputItems = items,
                tasteDNA = TasteDNA()
            )
            assertEquals(1, photoFilterResults.size)
            assertEquals("p1", photoFilterResults[0].id)

            val videoFilterResults = repository.getFilteredAndSortedMedia(
                filterType = "VIDEO",
                sortCategory = SortCategory.STANDARD,
                standardSort = StandardSortOption.NEWEST_FIRST,
                intelligentSort = IntelligentSortOption.PERSONALIZED,
                inputItems = items,
                tasteDNA = TasteDNA()
            )
            assertEquals(1, videoFilterResults.size)
            assertEquals("v1", videoFilterResults[0].id)

            val allFilterResults = repository.getFilteredAndSortedMedia(
                filterType = "ALL",
                sortCategory = SortCategory.STANDARD,
                standardSort = StandardSortOption.NEWEST_FIRST,
                intelligentSort = IntelligentSortOption.PERSONALIZED,
                inputItems = items,
                tasteDNA = TasteDNA()
            )
            assertEquals(2, allFilterResults.size)
        } finally {
            repository.close()
        }
    }

    @Test
    fun testPhotoAndVideoFilterAliases() = runTest(testDispatcher) {
        try {
            val photoItem = MediaItem(id = "p1", title = "Photo 1", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
            val videoItem = MediaItem(id = "v1", title = "Video 1", mediaType = "VIDEO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
            val movieItem = MediaItem(id = "v2", title = "Movie 1", mediaType = "MOVIE", compatibilityStatus = CompatibilityStatus.PLAYABLE)
            val imageItem = MediaItem(id = "p2", title = "Image 1", mediaType = "IMAGE", compatibilityStatus = CompatibilityStatus.PLAYABLE)
            val items = listOf(photoItem, videoItem, movieItem, imageItem)

            val photosPluralResults = repository.getFilteredAndSortedMedia(
                filterType = "PHOTOS",
                sortCategory = SortCategory.STANDARD,
                standardSort = StandardSortOption.NEWEST_FIRST,
                intelligentSort = IntelligentSortOption.PERSONALIZED,
                inputItems = items,
                tasteDNA = TasteDNA()
            )
            assertEquals(2, photosPluralResults.size)
            assertTrue(photosPluralResults.all { it.id == "p1" || it.id == "p2" })

            val videosPluralResults = repository.getFilteredAndSortedMedia(
                filterType = "VIDEOS",
                sortCategory = SortCategory.STANDARD,
                standardSort = StandardSortOption.NEWEST_FIRST,
                intelligentSort = IntelligentSortOption.PERSONALIZED,
                inputItems = items,
                tasteDNA = TasteDNA()
            )
            assertEquals(2, videosPluralResults.size)
            assertTrue(videosPluralResults.all { it.id == "v1" || it.id == "v2" })
        } finally {
            repository.close()
        }
    }
}
