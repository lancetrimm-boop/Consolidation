package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameSamplerTest {

    @Test
    fun testGetSampleTimestamps_ShortVideo() = runBlocking {
        val durationMs = 1000L
        val timestamps = VideoFrameSampler.getSampleTimestamps(durationMs)
        
        // Should sample middle
        assertEquals(1, timestamps.size)
        assertEquals(500_000L, timestamps[0])
    }

    @Test
    fun testGetSampleTimestamps_LongVideo() = runBlocking {
        val durationMs = 60_000L // 60s
        val timestamps = VideoFrameSampler.getSampleTimestamps(durationMs)
        
        // Default to deterministic Stage 8 fallback since context is null
        assertEquals(5, timestamps.size)
    }

    @Test
    fun testGetSampleTimestamps_ZeroDuration() = runBlocking {
        val timestamps = VideoFrameSampler.getSampleTimestamps(0)
        assertEquals(1, timestamps.size)
        assertEquals(1_000_000L, timestamps[0])
    }

    @Test
    fun testDeterministicSamplingSpacing_BoundsAndMidpoint() = runBlocking {
        val durationMs = 10_000L // 10s
        val timestamps = VideoFrameSampler.getSampleTimestamps(durationMs)
        
        assertEquals(5, timestamps.size)
        // Check order and boundaries (within 5% margin)
        for (i in 0 until timestamps.size - 1) {
            assertTrue(timestamps[i] < timestamps[i + 1])
        }
        // Midpoint check
        assertEquals(5_000_000L, timestamps[2])
    }
}

