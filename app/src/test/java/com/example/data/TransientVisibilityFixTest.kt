package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientVisibilityFixTest {

    private fun createItem(
        status: CompatibilityStatus,
        videoCodec: String = "",
        containerFormat: String = "",
        isDeleted: Boolean = false
    ): MediaItem {
        return MediaItem(
            id = "test_item_${status.name}",
            title = "Test Item",
            mediaType = "VIDEO",
            year = 2024,
            duration = "0:10",
            genre = "Media",
            imageUrl = "",
            rating = 0.0f,
            uriPath = "file://test.mp4",
            compatibilityStatus = status,
            videoCodec = videoCodec,
            containerFormat = containerFormat,
            isDeleted = isDeleted
        )
    }

    @Test
    fun testVisibleStatuses_EvaluateToTrue() {
        val repo = MediaRepository.instance

        assertTrue(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.PLAYABLE)))
        assertTrue(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE)))
        assertTrue(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.THUMBNAIL_FAILED)))
    }

    @Test
    fun testHiddenStatuses_EvaluateToFalse() {
        val repo = MediaRepository.instance

        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.ANALYSIS_PENDING)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.ANALYSIS_IN_PROGRESS)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.ANALYSIS_FAILED)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.UNTESTED)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.UNSUPPORTED)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.CORRUPT)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.UNREADABLE)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.PLAYABLE_AFTER_CONVERSION)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.NEEDS_TRANSCODE)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.DELETED)))
        assertFalse(repo.isItemVisibleInLibrary(createItem(CompatibilityStatus.REPLACED)))
    }

    @Test
    fun testStandardCodecs_DoNotOverrideUnsupportedStatus() {
        val repo = MediaRepository.instance

        // Standard H.264/HEVC codecs with UNSUPPORTED status must evaluate to FALSE
        assertFalse(
            repo.isItemVisibleInLibrary(
                createItem(
                    status = CompatibilityStatus.UNSUPPORTED,
                    videoCodec = "video/avc",
                    containerFormat = "MP4"
                )
            )
        )

        assertFalse(
            repo.isItemVisibleInLibrary(
                createItem(
                    status = CompatibilityStatus.UNSUPPORTED,
                    videoCodec = "video/hevc",
                    containerFormat = "MP4"
                )
            )
        )

        assertFalse(
            repo.isItemVisibleInLibrary(
                createItem(
                    status = CompatibilityStatus.UNSUPPORTED,
                    videoCodec = "h264",
                    containerFormat = "MOV"
                )
            )
        )

        assertFalse(
            repo.isItemVisibleInLibrary(
                createItem(
                    status = CompatibilityStatus.UNSUPPORTED,
                    videoCodec = "h265",
                    containerFormat = "MKV"
                )
            )
        )
    }

    @Test
    fun testDeletedItem_AlwaysEvaluatesToFalse() {
        val repo = MediaRepository.instance

        assertFalse(
            repo.isItemVisibleInLibrary(
                createItem(
                    status = CompatibilityStatus.PLAYABLE,
                    isDeleted = true
                )
            )
        )
    }
}
