package com.example.core.mediagate

import com.example.core.mediamodel.ClassifiedMediaItem
import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.TestStatus
import com.example.data.mediastore.DefaultDiagnosticMediaStore
import com.example.data.mediastore.DefaultPresentationMediaStore
import com.example.data.mediastore.IneligibleMediaEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaAdmissionGateTest {

    @Test
    fun testAdmissionGate_EligiblePathAdmitsMedia() = runTest {
        val presentationStore = DefaultPresentationMediaStore()
        val diagnosticStore = DefaultDiagnosticMediaStore()
        val provider = object : ClassifiedItemProvider {
            override suspend fun getClassifiedItem(id: String): ClassifiedMediaItem? = null
        }

        val gate = DefaultMediaAdmissionGate(
            presentationStore = presentationStore,
            diagnosticStore = diagnosticStore,
            itemProvider = provider
        )

        val item = ClassifiedMediaItem(
            id = "test-1",
            uri = "file://test.mp4",
            title = "Test Video",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 500L,
            lastModifiedEpochMs = 900L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = false,
            fileExists = true,
            hasReadPermission = true
        )

        gate.processClassifiedItem(item)

        val admitted = presentationStore.getById("test-1")
        assertNotNull(admitted)
        assertEquals("test-1", admitted?.id)
        assertEquals(1, presentationStore.eligibleMedia.value.size)
    }

    @Test
    fun testAdmissionGate_IneligiblePathRecordsAndRevokes() = runTest {
        val presentationStore = DefaultPresentationMediaStore()
        val diagnosticStore = DefaultDiagnosticMediaStore()
        val provider = object : ClassifiedItemProvider {
            override suspend fun getClassifiedItem(id: String): ClassifiedMediaItem? = null
        }

        val gate = DefaultMediaAdmissionGate(
            presentationStore = presentationStore,
            diagnosticStore = diagnosticStore,
            itemProvider = provider
        )

        val item = ClassifiedMediaItem(
            id = "test-2",
            uri = "file://corrupt.mp4",
            title = "Corrupt Video",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 500L,
            lastModifiedEpochMs = 900L,
            isAnalyzed = false, // ANALYSIS_PENDING
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = true, // CORRUPT
            fileExists = true,
            hasReadPermission = true
        )

        gate.processClassifiedItem(item)

        assertNull(presentationStore.getById("test-2"))
        val ineligibleList = diagnosticStore.ineligibleItems.value
        assertEquals(1, ineligibleList.size)
        assertTrue(ineligibleList[0].reasons.contains(IneligibilityReason.ANALYSIS_PENDING))
        assertTrue(ineligibleList[0].reasons.contains(IneligibilityReason.CORRUPT))
    }

    @Test
    fun testPresentationMediaStore_BatchRevokeProducesSingleEmission() = runTest {
        val store = DefaultPresentationMediaStore()
        val item1 = com.example.core.mediamodel.PresentationEligibleMedia.createInstance(
            "1", "uri1", "t1", 100L, "video/mp4", 100, 100, 10L, "hash1", 1
        )
        val item2 = com.example.core.mediamodel.PresentationEligibleMedia.createInstance(
            "2", "uri2", "t2", 200L, "video/mp4", 100, 100, 20L, "hash2", 1
        )
        store.batchAdmit(listOf(item1, item2))

        val emittedLists = mutableListOf<List<com.example.core.mediamodel.PresentationEligibleMedia>>()
        val collectJob = backgroundScope.launch {
            store.eligibleMedia.toList(emittedLists)
        }
        runCurrent()

        store.batchRevoke(setOf("1", "2"), setOf(IneligibilityReason.MISSING))
        runCurrent()
        collectJob.cancel()

        // Initial emission (2 items) + 1 update emission (empty list) = exactly 2 emissions total
        assertEquals(2, emittedLists.size)
        assertTrue(emittedLists.last().isEmpty())
    }

    @Test
    fun testDiagnosticMediaStore_RecordAndClear() = runTest {
        val diagnosticStore = DefaultDiagnosticMediaStore()
        val entry = IneligibleMediaEntry(
            id = "d1",
            filePath = "file://bad",
            title = "Bad",
            sizeBytes = 100L,
            reasons = setOf(IneligibilityReason.CORRUPT),
            detectedAtEpochMs = 123L
        )

        diagnosticStore.recordIneligible(entry)
        assertEquals(1, diagnosticStore.ineligibleItems.value.size)

        diagnosticStore.clearDiagnostic("d1")
        assertTrue(diagnosticStore.ineligibleItems.value.isEmpty())
    }
}
