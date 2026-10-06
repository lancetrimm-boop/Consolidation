package com.example.data

import com.example.data.metv.MeTVProgrammingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureDecayTest {

    private fun createItem(
        id: String,
        exposureCount: Int = 0,
        lastExposedTimestamp: Long? = null,
        dateAdded: Long = System.currentTimeMillis(),
        rating: Float = 4.0f,
        isFavorite: Boolean = false,
    ): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            uriPath = "path/$id",
            mediaType = "VIDEO",
            exposureCount = exposureCount,
            lastExposedTimestamp = lastExposedTimestamp,
            dateAdded = dateAdded,
            rating = rating,
            isFavorite = isFavorite,
            genre = "Cinematic",
        )
    }

    @Test
    fun testGetDecayedExposure_UnexposedItem_ReturnsZero() {
        val item = createItem("unexposed", exposureCount = 0)
        assertEquals(0f, item.getDecayedExposure(), 0.001f)
    }

    @Test
    fun testGetDecayedExposure_3DayHalfLifeDecay() {
        val now = 100_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L

        // Immediate exposure (0 days ago): 10.0
        val itemRecent = createItem("recent", exposureCount = 10, lastExposedTimestamp = now)
        assertEquals(10.0f, itemRecent.getDecayedExposure(currentTimeMs = now), 0.01f)

        // 3 days ago (1 half-life): ~5.0
        val item3Days = createItem("3days", exposureCount = 10, lastExposedTimestamp = now - (3 * dayMs))
        assertEquals(5.0f, item3Days.getDecayedExposure(currentTimeMs = now), 0.1f)

        // 6 days ago (2 half-lives): ~2.5
        val item6Days = createItem("6days", exposureCount = 10, lastExposedTimestamp = now - (6 * dayMs))
        assertEquals(2.5f, item6Days.getDecayedExposure(currentTimeMs = now), 0.1f)

        // 30 days ago (10 half-lives): ~0.0
        val item30Days = createItem("30days", exposureCount = 10, lastExposedTimestamp = now - (30 * dayMs))
        assertTrue(item30Days.getDecayedExposure(currentTimeMs = now) < 0.05f)
    }

    @Test
    fun testGetDecayedExposure_LegacyNullTimestamp_UsesDateAddedFallback() {
        val now = 100_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L

        // Legacy item added 6 days ago with null lastExposedTimestamp
        val legacyItem = createItem("legacy", exposureCount = 10, lastExposedTimestamp = null, dateAdded = now - (6 * dayMs))
        assertEquals(2.5f, legacyItem.getDecayedExposure(currentTimeMs = now), 0.1f)
    }

    @Test
    fun testMeTVEngine_RecentExposurePenalizedMoreThanOldExposure() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        val recentExposed = createItem("recent_exp", exposureCount = 5, lastExposedTimestamp = now, rating = 4.0f)
        val oldExposed = createItem("old_exp", exposureCount = 5, lastExposedTimestamp = now - (14 * dayMs), rating = 4.0f)

        val available = listOf(recentExposed, oldExposed)
        val engine = MeTVProgrammingEngine()
        val result = engine.program(
            availableMedia = available,
            currentTimeMs = now,
            exposureMap = available.associateBy({ it.id }) { it.exposureCount },
            decayedExposureMap = available.associateBy({ it.id }) { it.getDecayedExposure(now) },
        )

        // Old exposed item should rank higher than recently exposed item because decay reduced penalty
        assertEquals("old_exp", result.items.first().id)
    }
}
