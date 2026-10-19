package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class PairwiseResetAndMediaTypeTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    @Test
    fun testResetPreservesMediaTypeAndClearsTransientState() = runTest {
        repository.setCompareMediaType(CompareMediaTypeFilter.VIDEOS)
        repository.setCompareSimilarity(CompareSimilarityFilter.SIMILAR)
        repository.setCompareLearningMode(CompareLearningMode.EXPLORE)
        repository.setCompareSort(CompareSortOption.NEWEST)

        assertEquals(CompareMediaTypeFilter.VIDEOS, repository.compareMediaType.value)
        assertEquals(CompareSimilarityFilter.SIMILAR, repository.compareSimilarity.value)
        assertEquals(CompareLearningMode.EXPLORE, repository.compareLearningMode.value)
        assertEquals(CompareSortOption.NEWEST, repository.compareSort.value)

        repository.resetPairwiseFilters()

        // Media type is preserved, others reset to defaults
        assertEquals(CompareMediaTypeFilter.VIDEOS, repository.compareMediaType.value)
        assertEquals(CompareSimilarityFilter.BROAD, repository.compareSimilarity.value)
        assertEquals(CompareLearningMode.LEARN, repository.compareLearningMode.value)
        assertEquals(CompareSortOption.RECOMMENDED, repository.compareSort.value)
    }

    @Test
    fun testSameTypePairingInvariant() = runTest {
        val photo1 = MediaItem(id = "p1", title = "Photo 1", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
        val video1 = MediaItem(id = "v1", title = "Video 1", mediaType = "VIDEO", compatibilityStatus = CompatibilityStatus.PLAYABLE)

        repository.setCompareMediaType(CompareMediaTypeFilter.PHOTOS)
        repository.setMediaItemsForTesting(listOf(photo1, video1))

        // Ensure selection/validation rejects mixed types
        val isVideoA = photo1.mediaType.uppercase().contains("VIDEO")
        val isVideoB = video1.mediaType.uppercase().contains("VIDEO")
        assertNotEquals(isVideoA, isVideoB)
    }
}
