package com.example.ui.screens

import android.content.Context
import com.example.core.mediagate.DefaultMediaAdmissionGate
import com.example.core.mediagate.MediaAdmissionGate
import com.example.core.mediamodel.ClassifiedMediaItem
import com.example.core.mediamodel.TestStatus
import com.example.data.FirstRunScanState
import com.example.data.LibraryPreferences
import com.example.data.mediastore.DefaultDiagnosticMediaStore
import com.example.data.mediastore.DefaultPresentationMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FirstRunScanTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var presentationStore: DefaultPresentationMediaStore
    private lateinit var diagnosticStore: DefaultDiagnosticMediaStore
    private lateinit var admissionGate: MediaAdmissionGate

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mock(Context::class.java)
        val prefs = mock(android.content.SharedPreferences::class.java)
        val editor = mock(android.content.SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.getString(anyString(), anyString())).thenReturn(FirstRunScanState.NEVER_REQUESTED.name)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)

        presentationStore = DefaultPresentationMediaStore()
        diagnosticStore = DefaultDiagnosticMediaStore()

        admissionGate = DefaultMediaAdmissionGate(
            presentationStore = presentationStore,
            diagnosticStore = diagnosticStore,
            itemProvider = object : com.example.core.mediagate.ClassifiedItemProvider {
                override suspend fun getClassifiedItem(id: String): ClassifiedMediaItem? = null
            }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFirstRunState_InitialStateIsNeverRequested() {
        val libraryPrefs = LibraryPreferences(mockContext)
        assertEquals(FirstRunScanState.NEVER_REQUESTED, libraryPrefs.firstRunScanState.value)
    }

    @Test
    fun testFirstRunState_TransitionsCorrectly() {
        val libraryPrefs = LibraryPreferences(mockContext)

        libraryPrefs.setFirstRunScanState(FirstRunScanState.GRANTED_SCAN_PENDING)
        assertEquals(FirstRunScanState.GRANTED_SCAN_PENDING, libraryPrefs.firstRunScanState.value)

        libraryPrefs.setFirstRunScanState(FirstRunScanState.INITIAL_SCAN_COMPLETE)
        assertEquals(FirstRunScanState.INITIAL_SCAN_COMPLETE, libraryPrefs.firstRunScanState.value)

        libraryPrefs.setFirstRunScanState(FirstRunScanState.DENIED)
        assertEquals(FirstRunScanState.DENIED, libraryPrefs.firstRunScanState.value)

        libraryPrefs.setFirstRunScanState(FirstRunScanState.PERMISSION_REVOKED)
        assertEquals(FirstRunScanState.PERMISSION_REVOKED, libraryPrefs.firstRunScanState.value)
    }

    @Test
    fun testGate1Isolation_EligibleAdmitted_IneligibleQuarantined() = runTest(testDispatcher) {
        val validItem = ClassifiedMediaItem(
            id = "valid_1",
            uri = "file://valid.mp4",
            title = "Valid Video",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 2048L,
            lastModifiedEpochMs = 1000L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = false,
            fileExists = true,
            hasReadPermission = true
        )

        val corruptItem = ClassifiedMediaItem(
            id = "corrupt_1",
            uri = "file://corrupt.mp4",
            title = "Corrupt Video",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 2048L,
            lastModifiedEpochMs = 1000L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = true, // Corrupt
            fileExists = true,
            hasReadPermission = true
        )

        admissionGate.processClassifiedItem(validItem)
        admissionGate.processClassifiedItem(corruptItem)

        // Verify validItem enters PresentationMediaStore
        assertEquals(1, presentationStore.eligibleMedia.value.size)
        assertEquals("valid_1", presentationStore.eligibleMedia.value[0].id)

        // Verify corruptItem is blocked from PresentationMediaStore and placed in DiagnosticMediaStore
        assertNull(presentationStore.getById("corrupt_1"))
        assertEquals(1, diagnosticStore.ineligibleItems.value.size)
        assertEquals("corrupt_1", diagnosticStore.ineligibleItems.value[0].id)
    }

    @Test
    fun testZeroEligibleMedia_DoesNotPreventScanCompletion() = runTest(testDispatcher) {
        val corruptItem = ClassifiedMediaItem(
            id = "corrupt_1",
            uri = "file://corrupt.mp4",
            title = "Corrupt Video",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            fileSizeBytes = 2048L,
            lastModifiedEpochMs = 1000L,
            isAnalyzed = true,
            testStatus = TestStatus.TESTED,
            isCodecSupported = true,
            isCorrupt = true,
            fileExists = true,
            hasReadPermission = true
        )

        admissionGate.processClassifiedItem(corruptItem)

        // Presentation Store is empty (0 items)
        assertEquals(0, presentationStore.eligibleMedia.value.size)

        // State can still transition to INITIAL_SCAN_COMPLETE
        val libraryPrefs = LibraryPreferences(mockContext)
        libraryPrefs.setFirstRunScanState(FirstRunScanState.INITIAL_SCAN_COMPLETE)
        assertEquals(FirstRunScanState.INITIAL_SCAN_COMPLETE, libraryPrefs.firstRunScanState.value)
    }
}
