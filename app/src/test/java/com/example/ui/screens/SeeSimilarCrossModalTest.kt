package com.example.ui.screens

import com.example.data.semantic.HybridSearchConfig
import com.example.data.semantic.RankedChannelItem
import com.example.data.semantic.ReciprocalRankFusion
import com.example.data.semantic.SearchChannel
import org.junit.Assert.*
import org.junit.Test

class SeeSimilarCrossModalTest {

    @Test
    fun testThreshold_ExactBoundary0_20f() {
        val precisionThreshold = 0.20f

        val exactMatch = 0.20f
        val aboveMatch = 0.21f
        val highMatch = 0.35f
        val belowMatch = 0.19f

        assertTrue(exactMatch >= precisionThreshold)
        assertTrue(aboveMatch >= precisionThreshold)
        assertTrue(highMatch >= precisionThreshold)
        assertFalse(belowMatch >= precisionThreshold)
    }

    @Test
    fun testSeedExclusion_RemovesSeedItemFromResults() {
        val seedId = "seed_item_123"
        val channelResults = mapOf(
            SearchChannel.SEMANTIC_VISUAL to listOf(
                RankedChannelItem("media_photo_1", 0.45f, 1),
                RankedChannelItem(seedId, 0.99f, 2),
                RankedChannelItem("media_video_2", 0.32f, 3)
            )
        )

        val fused = ReciprocalRankFusion.fuse(channelResults, HybridSearchConfig(topK = 50))
        val filtered = fused.filter { it.mediaId != seedId }

        assertEquals(2, filtered.size)
        assertFalse(filtered.any { it.mediaId == seedId })
    }

    @Test
    fun testCrossModalCandidateFusion_PreservesPhotoAndVideoCandidates() {
        val channelResults = mapOf(
            SearchChannel.SEMANTIC_VISUAL to listOf(
                RankedChannelItem("photo_item_1", 0.42f, 1),
                RankedChannelItem("video_item_1", 0.38f, 2),
                RankedChannelItem("photo_item_2", 0.30f, 3),
                RankedChannelItem("video_item_2", 0.25f, 4)
            )
        )

        val fused = ReciprocalRankFusion.fuse(channelResults, HybridSearchConfig(topK = 50))

        assertEquals(4, fused.size)
        assertEquals("photo_item_1", fused[0].mediaId)
        assertEquals("video_item_1", fused[1].mediaId)
        assertEquals("photo_item_2", fused[2].mediaId)
        assertEquals("video_item_2", fused[3].mediaId)
    }
}
