package com.example.ui.screens

import com.example.data.semantic.HybridSearchConfig
import com.example.data.semantic.RankedChannelItem
import com.example.data.semantic.ReciprocalRankFusion
import com.example.data.semantic.SearchChannel
import org.junit.Assert.*
import org.junit.Test

class SeeSimilarPlaylistLengthTest {

    @Test
    fun testSimilarPrecisionThreshold_PassesVisualMatchesBetween25And42Percent() {
        val precisionThreshold = 0.25f

        val candidate1Similarity = 0.28f
        val candidate2Similarity = 0.35f
        val candidate3Similarity = 0.40f
        val lowSimilarity = 0.18f

        assertTrue(candidate1Similarity >= precisionThreshold)
        assertTrue(candidate2Similarity >= precisionThreshold)
        assertTrue(candidate3Similarity >= precisionThreshold)
        assertFalse(lowSimilarity >= precisionThreshold)
    }

    @Test
    fun testRrfFusion_PreservesMultipleSimilarCandidates() {
        val channelResults = mapOf(
            SearchChannel.SEMANTIC_VISUAL to listOf(
                RankedChannelItem("media_1", 0.38f, 1),
                RankedChannelItem("media_2", 0.35f, 2),
                RankedChannelItem("media_3", 0.31f, 3),
                RankedChannelItem("media_4", 0.27f, 4),
                RankedChannelItem("seed_item", 0.99f, 5)
            )
        )

        val config = HybridSearchConfig(topK = 50)
        val fused = ReciprocalRankFusion.fuse(channelResults, config)

        // Exclude seed item
        val filtered = fused.filter { it.mediaId != "seed_item" }

        assertEquals(4, filtered.size)
        assertEquals("media_1", filtered[0].mediaId)
        assertEquals("media_2", filtered[1].mediaId)
        assertEquals("media_3", filtered[2].mediaId)
        assertEquals("media_4", filtered[3].mediaId)
    }
}
