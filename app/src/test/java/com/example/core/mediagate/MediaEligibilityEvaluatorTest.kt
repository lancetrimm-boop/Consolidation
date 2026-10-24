package com.example.core.mediagate

import com.example.core.mediamodel.EligibilityResult
import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.TestStatus
import com.example.core.mediamodel.ClassifiedMediaItem
import org.junit.Assert.*
import org.junit.Test

class MediaEligibilityEvaluatorTest {

    @Test
    fun testEvaluator_EligibleWhenAllChecksPass() {
        val item = ClassifiedMediaItem(
            id = "1",
            uri = "file://test",
            title = "Test",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 123456789L,
            fileSizeBytes = 1024L,
            lastModifiedEpochMs = 123456789L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = false,
            fileExists = true,
            hasReadPermission = true
        )

        val result = MediaEligibilityEvaluator.evaluate(item)
        assertEquals(EligibilityResult.Eligible, result)
    }

    @Test
    fun testEvaluator_MultipleReasonsAtOnce() {
        val item = ClassifiedMediaItem(
            id = "2",
            uri = "file://test",
            title = "Test",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 123456789L,
            fileSizeBytes = 1024L,
            lastModifiedEpochMs = 123456789L,
            isAnalyzed = false, // ANALYSIS_PENDING
            testStatus = TestStatus.UNTESTED, // UNTESTED
            isCodecSupported = false, // UNSUPPORTED_CODEC
            isCorrupt = true, // CORRUPT
            fileExists = false, // MISSING
            hasReadPermission = false // INACCESSIBLE
        )

        val result = MediaEligibilityEvaluator.evaluate(item)
        assertTrue(result is EligibilityResult.Ineligible)
        val reasons = (result as EligibilityResult.Ineligible).reasons
        assertEquals(6, reasons.size)
        assertTrue(reasons.contains(IneligibilityReason.ANALYSIS_PENDING))
        assertTrue(reasons.contains(IneligibilityReason.UNTESTED))
        assertTrue(reasons.contains(IneligibilityReason.UNSUPPORTED_CODEC))
        assertTrue(reasons.contains(IneligibilityReason.CORRUPT))
        assertTrue(reasons.contains(IneligibilityReason.MISSING))
        assertTrue(reasons.contains(IneligibilityReason.INACCESSIBLE))
    }

    @Test
    fun testSnapshotHasher_StableOutput() {
        val item1 = ClassifiedMediaItem(
            id = "1",
            uri = "file://test",
            title = "Test",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 123456789L,
            fileSizeBytes = 1024L,
            lastModifiedEpochMs = 123456789L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = false,
            fileExists = true,
            hasReadPermission = true
        )
        val item2 = item1.copy(title = "Different Title") // title not part of hasher input

        val hasher = DefaultSnapshotHasher()
        val hash1 = hasher.compute(item1)
        val hash2 = hasher.compute(item2)

        assertEquals(16, hash1.length)
        assertEquals(hash1, hash2)
    }
}
