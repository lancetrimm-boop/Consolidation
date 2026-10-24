package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VisualIndexReadinessTest {

    @Test
    fun testVisualIndexReadiness_GreenReady_100Percent() {
        val readiness = VisualIndexReadiness(
            state = VisualIndexState.GREEN_READY,
            totalEligible = 100,
            verifiedVisualCount = 100,
            pendingCount = 0,
            processingCount = 0,
            failedCount = 0,
            label = "Visual Index Ready (100%)",
            isModelReady = true
        )
        assertEquals(VisualIndexState.GREEN_READY, readiness.state)
        assertEquals("Visual Index Ready (100%)", readiness.label)
        assertEquals(100, readiness.verifiedVisualCount)
    }

    @Test
    fun testVisualIndexReadiness_GreenReady_WithSkips() {
        val verified = 11306
        val skipped = 519
        val total = 11825
        val label = "Visual Index Ready — $verified indexed, $skipped skipped"

        val readiness = VisualIndexReadiness(
            state = VisualIndexState.GREEN_READY,
            totalEligible = total,
            verifiedVisualCount = verified,
            pendingCount = 0,
            processingCount = 0,
            failedCount = 0,
            label = label,
            isModelReady = true
        )
        assertEquals(VisualIndexState.GREEN_READY, readiness.state)
        assertEquals("Visual Index Ready — 11306 indexed, 519 skipped", readiness.label)
        assertEquals(verified + skipped, readiness.totalEligible)
    }

    @Test
    fun testVisualIndexReadiness_YellowIndexing() {
        val verified = 50
        val total = 100
        val pct = (verified * 100) / total
        val label = "Preparing Visual Search: $verified / $total ($pct%)"

        val readiness = VisualIndexReadiness(
            state = VisualIndexState.YELLOW_INDEXING,
            totalEligible = total,
            verifiedVisualCount = verified,
            pendingCount = 50,
            processingCount = 0,
            failedCount = 0,
            label = label,
            isModelReady = true
        )
        assertEquals(VisualIndexState.YELLOW_INDEXING, readiness.state)
        assertEquals("Preparing Visual Search: 50 / 100 (50%)", readiness.label)
    }

    @Test
    fun testVisualIndexReadiness_RedIncomplete() {
        val readiness = VisualIndexReadiness(
            state = VisualIndexState.RED_INCOMPLETE,
            totalEligible = 100,
            verifiedVisualCount = 80,
            pendingCount = 0,
            processingCount = 0,
            failedCount = 20,
            label = "Visual Index Incomplete (20 Failed) — Tap to Retry",
            isModelReady = true
        )
        assertEquals(VisualIndexState.RED_INCOMPLETE, readiness.state)
        assertEquals(20, readiness.failedCount)
    }

    @Test
    fun testVisualIndexReadiness_NeutralEmpty() {
        val readiness = VisualIndexReadiness(
            state = VisualIndexState.NEUTRAL_EMPTY,
            totalEligible = 0,
            verifiedVisualCount = 0,
            pendingCount = 0,
            processingCount = 0,
            failedCount = 0,
            label = "No Eligible Media",
            isModelReady = true
        )
        assertEquals(VisualIndexState.NEUTRAL_EMPTY, readiness.state)
        assertEquals("No Eligible Media", readiness.label)
    }
}
