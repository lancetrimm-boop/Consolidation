package com.example.ui.screens

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import org.junit.Assert.*
import org.junit.Test

class UniversalPlayableVisibilityContractTest {

    @Test
    fun testThumbnailFailed_IsLibraryVisibleButCandidateIneligible() {
        val thumbnailFailedItem = MediaItem(
            id = "item_tf",
            title = "Thumbnail Failed Item",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.THUMBNAIL_FAILED
        )

        // Library visible = true
        val isVisibleInLibrary = when (thumbnailFailedItem.compatibilityStatus) {
            CompatibilityStatus.PLAYABLE,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE,
            CompatibilityStatus.THUMBNAIL_FAILED -> true
            else -> false
        }
        assertTrue(isVisibleInLibrary)

        // Candidate eligible = false
        val isCandidateEligible = when (thumbnailFailedItem.compatibilityStatus) {
            CompatibilityStatus.PLAYABLE,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE -> true
            else -> false
        }
        assertFalse(isCandidateEligible)
    }

    @Test
    fun testIneligibleStatuses_AreExcludedFromCandidates() {
        val ineligibleStatuses = listOf(
            CompatibilityStatus.UNSUPPORTED,
            CompatibilityStatus.CORRUPT,
            CompatibilityStatus.UNREADABLE,
            CompatibilityStatus.ANALYSIS_PENDING,
            CompatibilityStatus.ANALYSIS_IN_PROGRESS,
            CompatibilityStatus.ANALYSIS_FAILED,
            CompatibilityStatus.UNTESTED,
            CompatibilityStatus.NEEDS_TRANSCODE,
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            CompatibilityStatus.DELETED,
            CompatibilityStatus.REPLACED
        )

        ineligibleStatuses.forEach { status ->
            val item = MediaItem(
                id = "item_${status.name}",
                title = "Item ${status.name}",
                mediaType = "PHOTO",
                compatibilityStatus = status
            )

            val isCandidateEligible = when (item.compatibilityStatus) {
                CompatibilityStatus.PLAYABLE,
                CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE -> true
                else -> false
            }
            assertFalse("Status ${status.name} must not be candidate-eligible", isCandidateEligible)
        }
    }

    @Test
    fun testPlayableStatuses_AreCandidateEligible() {
        val playableStatuses = listOf(
            CompatibilityStatus.PLAYABLE,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE
        )

        playableStatuses.forEach { status ->
            val item = MediaItem(
                id = "item_${status.name}",
                title = "Item ${status.name}",
                mediaType = "VIDEO",
                compatibilityStatus = status
            )

            val isCandidateEligible = when (item.compatibilityStatus) {
                CompatibilityStatus.PLAYABLE,
                CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE -> true
                else -> false
            }
            assertTrue("Status ${status.name} must be candidate-eligible", isCandidateEligible)
        }
    }

    @Test
    fun testPairwiseSelectedIds_FiltersOutIneligibleItems() {
        val selectedItems = listOf(
            MediaItem(id = "valid_p1", title = "Playable P1", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE),
            MediaItem(id = "corrupt_p2", title = "Corrupt P2", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.CORRUPT),
            MediaItem(id = "unsupported_p3", title = "Unsupported P3", mediaType = "VIDEO", compatibilityStatus = CompatibilityStatus.UNSUPPORTED),
            MediaItem(id = "tf_p4", title = "Thumbnail Failed P4", mediaType = "VIDEO", compatibilityStatus = CompatibilityStatus.THUMBNAIL_FAILED)
        )

        val filtered = selectedItems.filter { item ->
            val isEligible = when (item.compatibilityStatus) {
                CompatibilityStatus.PLAYABLE,
                CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE -> true
                else -> false
            }
            val isVisible = when (item.compatibilityStatus) {
                CompatibilityStatus.PLAYABLE,
                CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE,
                CompatibilityStatus.THUMBNAIL_FAILED -> true
                else -> false
            }
            isEligible && isVisible
        }

        assertEquals(1, filtered.size)
        assertEquals("valid_p1", filtered[0].id)
    }
}
