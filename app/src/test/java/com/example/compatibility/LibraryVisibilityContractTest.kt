package com.example.compatibility

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryVisibilityContractTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = MediaRepository(dispatcher)

    @Test
    fun testLibraryVisibilityContractForAllStatuses() {
        val testCases = listOf(
            CompatibilityStatus.PLAYABLE to true,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE to true,
            CompatibilityStatus.THUMBNAIL_FAILED to true,
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION to false,
            CompatibilityStatus.NEEDS_TRANSCODE to false,
            CompatibilityStatus.UNSUPPORTED to false,
            CompatibilityStatus.UNTESTED to false,
            CompatibilityStatus.ANALYSIS_PENDING to false,
            CompatibilityStatus.CORRUPT to false,
            CompatibilityStatus.UNREADABLE to false,
            CompatibilityStatus.DELETED to false,
            CompatibilityStatus.REPLACED to false
        )

        for ((status, expectedVisible) in testCases) {
            val item = MediaItem(
                id = "item_${status.name}",
                title = "Test ${status.name}",
                mediaType = "VIDEO",
                compatibilityStatus = status,
                uriPath = "file:///storage/emulated/0/Download/test.mp4",
                videoCodec = "video/avc"
            )
            val isVisible = repository.isItemVisibleInLibrary(item)
            if (expectedVisible) {
                assertTrue("Status ${status.name} must be visible in Library", isVisible)
            } else {
                assertFalse("Status ${status.name} must NOT be visible in Library", isVisible)
            }
        }
    }
}
