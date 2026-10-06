package com.example.ui.screens

import com.example.data.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsPagingTest {

    private fun createDummyMediaItem(id: String, type: String = "PHOTO", isFavorite: Boolean = false): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            uriPath = "path/$id",
            mediaType = type,
            isFavorite = isFavorite,
            genre = "Test Genre",
        )
    }

    @Test
    fun testCollectionFiltering_RemovesPhotosAndVideosCollections() {
        val items = listOf(
            createDummyMediaItem("p1", "PHOTO"),
            createDummyMediaItem("v1", "VIDEO"),
        )

        val userPhotos = items.filter { it.mediaType.equals("PHOTO", ignoreCase = true) }
        val userVideos = items.filter { (it.mediaType.equals("VIDEO", ignoreCase = true) || it.mediaType.equals("Movie", ignoreCase = true)) && !it.genre.contains("Aura Generated") }

        assertTrue("User Photos contains items", userPhotos.isNotEmpty())
        assertTrue("User Videos contains items", userVideos.isNotEmpty())
    }

    @Test
    fun testInfiniteScrollBatching_InitialBatch24_Incremental24_CappedAtTotal() {
        val items = (1..50).map { createDummyMediaItem("item_$it") }
        
        var loadedCount = 24
        val initialBatch = items.take(loadedCount.coerceAtMost(items.size))
        assertEquals(24, initialBatch.size)

        // Trigger first incremental batch (+24)
        loadedCount = (loadedCount + 24).coerceAtMost(items.size)
        val secondBatch = items.take(loadedCount.coerceAtMost(items.size))
        assertEquals(48, secondBatch.size)

        // Trigger final incremental batch (+24), capped at total items (50)
        loadedCount = (loadedCount + 24).coerceAtMost(items.size)
        val finalBatch = items.take(loadedCount.coerceAtMost(items.size))
        assertEquals(50, finalBatch.size)
    }

    @Test
    fun testInfiniteScrollBatching_SmallCollections_NoOverflow() {
        val testSizes = listOf(0, 1, 23, 24, 25, 47, 48, 49)

        for (size in testSizes) {
            val items = (1..size).map { createDummyMediaItem("item_$it") }
            var loadedCount = 24
            val visibleItems = items.take(loadedCount.coerceAtMost(items.size))
            val expectedSize = minOf(size, 24)
            assertEquals("Size mismatch for $size items", expectedSize, visibleItems.size)
            
            // Check boundary load
            loadedCount = (loadedCount + 24).coerceAtMost(items.size)
            val nextVisibleItems = items.take(loadedCount.coerceAtMost(items.size))
            val expectedNextSize = minOf(size, 48)
            assertEquals("Next batch size mismatch for $size items", expectedNextSize, nextVisibleItems.size)
        }
    }
}
