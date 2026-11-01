package com.example.data.worker

import android.content.Context
import androidx.work.WorkerParameters
import com.example.core.mediagate.ClassifiedItemProvider
import com.example.core.mediagate.DefaultSnapshotHasher
import com.example.core.mediagate.FileSystemValidator
import com.example.core.mediagate.MediaConsumptionGate
import com.example.core.mediagate.MediaEligibilityEvaluator
import com.example.core.mediamodel.ClassifiedMediaItem
import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.TestStatus
import com.example.data.MediaItem
import com.example.data.TasteDNA
import com.example.data.IntelligenceStats
import com.example.data.mediastore.DefaultDiagnosticMediaStore
import com.example.data.mediastore.DefaultPresentationMediaStore
import com.example.data.mediastore.IneligibleMediaEntry
import com.example.data.metv.MeTVCandidate
import com.example.data.metv.MeTVPlaybackController
import com.example.ui.screens.CleanupReviewViewModel
import com.example.data.entitlement.EntitlementRepository
import com.example.data.entitlement.ProFeature
import com.example.data.entitlement.ProState
import com.example.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class Phase6BoundaryCompletionTest {

    private lateinit var store: DefaultPresentationMediaStore
    private lateinit var diagnosticStore: DefaultDiagnosticMediaStore
    private lateinit var gate: MediaConsumptionGate
    private lateinit var controller: MeTVPlaybackController

    @Before
    fun setUp() {
        store = DefaultPresentationMediaStore()
        diagnosticStore = DefaultDiagnosticMediaStore()
        val validator = object : FileSystemValidator {
            override suspend fun isAccessible(uriString: String): Boolean = true
        }
        gate = MediaConsumptionGate(store, validator)
        controller = MeTVPlaybackController(store, gate)
    }

    @Test
    fun testAuditWorker_PurgesIneligibleCandidates() = runTest {
        val eligibleItem = ClassifiedMediaItem(
            id = "valid_1",
            uri = "file://valid_1.mp4",
            title = "Valid",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 1000L,
            lastModifiedEpochMs = 1000L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = false,
            fileExists = true,
            hasReadPermission = true
        )
        val ineligibleItem = ClassifiedMediaItem(
            id = "ineligible_1",
            uri = "file://corrupt.mp4",
            title = "Corrupt",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 1000L,
            lastModifiedEpochMs = 1000L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = true, // Failure due to corruption
            fileExists = true,
            hasReadPermission = true
        )

        val itemMap = mapOf(
            "valid_1" to eligibleItem,
            "ineligible_1" to ineligibleItem
        )
        val itemProvider = object : ClassifiedItemProvider {
            override suspend fun getClassifiedItem(id: String): ClassifiedMediaItem? = itemMap[id]
        }

        val dummyMedia1 = MediaItem(id = "valid_1", title = "Valid", mediaType = "VIDEO")
        val dummyMedia2 = MediaItem(id = "ineligible_1", title = "Corrupt", mediaType = "VIDEO")

        val cand1 = MeTVCandidate("valid_1", dummyMedia1, gateVersion = 0, snapshotHash = "")
        val cand2 = MeTVCandidate("ineligible_1", dummyMedia2, gateVersion = 0, snapshotHash = "")
        controller.enqueueCandidates(listOf(cand1, cand2))

        val mockContext = mock(Context::class.java)
        val mockParams = mock(WorkerParameters::class.java)

        val worker = MediaEligibilityAuditWorker(
            appContext = mockContext,
            workerParams = mockParams,
            itemProvider = itemProvider,
            playbackController = controller
        )

        val audited = worker.auditCandidates(listOf(cand1, cand2))

        // Verify ineligible_1 was purged from controller queue
        assertEquals(1, audited.size)
        assertEquals("valid_1", audited[0].mediaId)
        assertEquals(1, controller.currentQueue.size)
        assertEquals("valid_1", controller.currentQueue[0].mediaId)
    }

    @Test
    fun testAuditWorker_UpdatesMetadataOnEligibleCandidates() = runTest {
        val eligibleItem = ClassifiedMediaItem(
            id = "valid_1",
            uri = "file://valid_1.mp4",
            title = "Valid",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 1000L,
            lastModifiedEpochMs = 1000L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = false,
            fileExists = true,
            hasReadPermission = true
        )

        val itemProvider = object : ClassifiedItemProvider {
            override suspend fun getClassifiedItem(id: String): ClassifiedMediaItem = eligibleItem
        }

        val dummyMedia = MediaItem(id = "valid_1", title = "Valid", mediaType = "VIDEO")
        val staleCandidate = MeTVCandidate("valid_1", dummyMedia, gateVersion = 0, snapshotHash = "STALE")

        val mockContext = mock(Context::class.java)
        val mockParams = mock(WorkerParameters::class.java)

        val worker = MediaEligibilityAuditWorker(
            appContext = mockContext,
            workerParams = mockParams,
            itemProvider = itemProvider,
            playbackController = controller
        )

        val audited = worker.auditCandidates(listOf(staleCandidate))

        assertEquals(1, audited.size)
        val freshCandidate = audited[0]
        assertEquals(MediaEligibilityEvaluator.CURRENT_CONTRACT_VERSION, freshCandidate.gateVersion)
        val expectedHash = DefaultSnapshotHasher().compute(eligibleItem)
        assertEquals(expectedHash, freshCandidate.snapshotHash)
    }

    @Test
    fun testCleanupViewModel_OnlySeesDiagnosticMediaStoreData() = runTest {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            val mockRepo = mock(MediaRepository::class.java)
            val mockEntitlementRepo = mock(EntitlementRepository::class.java)

            `when`(mockRepo.diagnosticMediaStore).thenReturn(diagnosticStore)
            `when`(mockRepo.databaseState).thenReturn(MutableStateFlow(com.example.data.DatabaseState.READY))
            `when`(mockRepo.mediaItems).thenReturn(MutableStateFlow(emptyList()))
            `when`(mockRepo.tasteDNA).thenReturn(MutableStateFlow(TasteDNA()))
            `when`(mockRepo.intelligenceStats).thenReturn(MutableStateFlow(IntelligenceStats()))
            whenever(mockRepo.getSkipCounts(anyList())).thenReturn(emptyMap<String, Int>())
            `when`(mockEntitlementRepo.proState).thenReturn(MutableStateFlow(ProState.Pro()))
            `when`(mockEntitlementRepo.isFeatureAvailable(ProFeature.CLEANUP_AUTOMATION)).thenReturn(true)

            val entry = IneligibleMediaEntry(
                id = "diag_1",
                filePath = "file://corrupt.mp4",
                title = "Corrupt File",
                sizeBytes = 2048L,
                reasons = setOf(IneligibilityReason.CORRUPT, IneligibilityReason.INACCESSIBLE),
                detectedAtEpochMs = 10000L
            )
            diagnosticStore.recordIneligible(entry)

            val viewModel = CleanupReviewViewModel(
                repository = mockRepo,
                entitlementRepository = mockEntitlementRepo,
                backgroundDispatcher = testDispatcher,
                diagnosticMediaStore = diagnosticStore
            )

            viewModel.loadRecommendations()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.recommendations.size)
            val rec = state.recommendations[0]
            assertEquals("diag_1", rec.mediaId)
            assertEquals(2048L, rec.storageSize)
            assertTrue(rec.explanation.contains("CORRUPT"))
            assertTrue(rec.explanation.contains("INACCESSIBLE"))
        } finally {
            Dispatchers.resetMain()
        }
    }
}
