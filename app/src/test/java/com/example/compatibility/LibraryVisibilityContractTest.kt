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

    @Test
    fun testStaleCompatibilityReconciliation_StandardCodecVisible_AviHidden() {
        val staleSupportedItem = MediaItem(
            id = "stale_sup",
            title = "Stale Supported Video",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.UNSUPPORTED,
            uriPath = "file:///storage/emulated/0/Download/video.mp4",
            videoCodec = "video/avc",
            containerFormat = "MP4"
        )
        assertTrue("Stale persisted UNSUPPORTED with standard codec must be recognized as playable in Library", repository.isItemVisibleInLibrary(staleSupportedItem))

        val genuineUnsupportedAvi = MediaItem(
            id = "gen_avi",
            title = "Genuine Unsupported AVI",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.UNSUPPORTED,
            uriPath = "file:///storage/emulated/0/Download/video.avi",
            videoCodec = "video/mpeg4",
            containerFormat = "AVI"
        )
        assertFalse("Genuine unsupported AVI must remain hidden from Library", repository.isItemVisibleInLibrary(genuineUnsupportedAvi))
    }
}
