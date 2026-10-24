package com.example

import com.example.data.*
import com.example.data.intelligence.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoverRecommendationTest {

    private val tasteDNA = TasteDNA()
    private val stats = IntelligenceStats()

    private val favoriteItem = MediaItem(
        id = "fav",
        title = "Favorite",
        mediaType = "VIDEO",
        genre = "Action",
        moodTags = listOf("vibrant"), 
        isFavorite = true,
        viewCount = 10,
        rating = 5.0f,
        compatibilityStatus = CompatibilityStatus.PLAYABLE
    )

    private val unseenItem = MediaItem(
        id = "new",
        title = "New",
        mediaType = "VIDEO",
        genre = "Documentary",
        moodTags = listOf("muted"),
        viewCount = 0,
        exposureCount = 0,
        compatibilityStatus = CompatibilityStatus.PLAYABLE
    )

    private fun setupMockRepository(): MediaRepository {
        val repository = mock(MediaRepository::class.java)
        val core = mock(AuraIntelligenceCore::class.java)
        
        whenever(repository.intelligenceCore).thenReturn(core)
        whenever(repository.mediaItems).thenReturn(MutableStateFlow(listOf(favoriteItem, unseenItem)))
        
        return repository
    }

    @Test
    fun testDiscover_PersonalizedMode_FavorsFavorites() {
        runBlocking {
            val repository = setupMockRepository()
            val core = repository.intelligenceCore!!

            // Mock Core response for Hero category
            whenever(core.processRequest(any())).thenAnswer { invocation ->
                val req = invocation.arguments[0] as IntelligenceRequest
                val candidates = if (req.limit == 1) {
                    listOf(IntelligenceCandidate(favoriteItem.copy(selectionReason = "High predicted match"), emptyList(), 1.0, 1.0f, 0f, "High predicted match"))
                } else {
                    listOf(
                        IntelligenceCandidate(favoriteItem.copy(selectionReason = "Similar to favorites"), emptyList(), 1.0, 1.0f, 0f, "Similar to favorites"),
                        IntelligenceCandidate(unseenItem.copy(selectionReason = "Fresh for you"), emptyList(), 0.5, 0.5f, 0f, "Fresh for you")
                    )
                }
                IntelligenceResponse(req.requestId, req.mode, candidates, latencyMs = 0L)
            }

            val categories = RecommendationEngine.computeDiscoverCategories(
                repository = repository,
                tasteDNA = tasteDNA,
                stats = stats
            )

            // In personalized mode, the high predicted match (favorite) should be next obsession
            assertEquals("fav", categories.nextObsession?.id)
            assertEquals("High predicted match", categories.nextObsession?.selectionReason)
        }
    }

    @Test
    fun testDiscover_ExploratoryMode_FavorsUnseen() {
        runBlocking {
            val repository = setupMockRepository()
            val core = repository.intelligenceCore!!

            // Mock Core response where unseen item is hero
            whenever(core.processRequest(any())).thenAnswer { invocation ->
                val req = invocation.arguments[0] as IntelligenceRequest
                val candidates = if (req.limit == 1) {
                    listOf(IntelligenceCandidate(unseenItem.copy(selectionReason = "Aura is learning your preference"), emptyList(), 1.0, 1.0f, 0f, "Aura is learning your preference"))
                } else {
                    listOf(
                        IntelligenceCandidate(unseenItem.copy(selectionReason = "Fresh for you"), emptyList(), 1.0, 1.0f, 0f, "Fresh for you"),
                        IntelligenceCandidate(favoriteItem.copy(selectionReason = "Similar to favorites"), emptyList(), 0.5, 0.5f, 0f, "Similar to favorites")
                    )
                }
                IntelligenceResponse(req.requestId, req.mode, candidates, latencyMs = 0L)
            }

            val categories = RecommendationEngine.computeDiscoverCategories(
                repository = repository,
                tasteDNA = tasteDNA,
                stats = stats
            )

            // In exploratory mode, the unseen item should rise to Next Obsession
            assertEquals("new", categories.nextObsession?.id)
            assertTrue(categories.nextObsession?.selectionReason?.contains("Aura is learning") == true)
        }
    }

    @Test
    fun testUnplayableMedia_ExcludedFromDiscover_ValidMediaRetained() {
        val corruptVideo = MediaItem(
            id = "corrupt_vid",
            title = "Corrupt Video",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.CORRUPT
        )
        val unsupportedVideo = MediaItem(
            id = "unsupported_vid",
            title = "Unsupported Video",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.UNSUPPORTED
        )
        val validVideo = MediaItem(
            id = "valid_vid",
            title = "Valid Video",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            uriPath = "https://example.com/video.mp4"
        )
        val validPhoto = MediaItem(
            id = "valid_photo",
            title = "Valid Photo",
            mediaType = "PHOTO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            uriPath = "https://example.com/photo.jpg"
        )

        val realRepo = MediaRepository(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        realRepo.setMediaItemsForTesting(listOf(corruptVideo, unsupportedVideo, validVideo, validPhoto))

        // Verify Library visibility vs Discover eligibility split
        assertFalse("Corrupt video must not be visible in Library", realRepo.isItemVisibleInLibrary(corruptVideo))
        assertFalse("Unsupported video must not be visible in Library", realRepo.isItemVisibleInLibrary(unsupportedVideo))
        assertTrue("Valid video must be visible in Library", realRepo.isItemVisibleInLibrary(validVideo))
        assertTrue("Valid photo must be visible in Library", realRepo.isItemVisibleInLibrary(validPhoto))

        val visible = realRepo.visibleMediaItems.value
        assertFalse("Corrupt video must be excluded from Library visible items", visible.any { it.id == "corrupt_vid" })
        assertFalse("Unsupported video must be excluded from Library visible items", visible.any { it.id == "unsupported_vid" })
        assertTrue("Valid video must remain in Library visible items", visible.any { it.id == "valid_vid" })
        assertTrue("Valid photo must remain in Library visible items", visible.any { it.id == "valid_photo" })
    }

    @Test
    fun testPullToRefresh_ExcludesThumbnailFailedAndCorrupt_RetainsValidMedia() {
        val thumbnailFailedVid = MediaItem(
            id = "tf_vid",
            title = "Thumbnail Failed Video",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.THUMBNAIL_FAILED,
            uriPath = "https://example.com/tf_video.mp4"
        )
        val corruptVid = MediaItem(
            id = "corrupt_vid",
            title = "Corrupt Video",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.CORRUPT,
            uriPath = "https://example.com/corrupt_video.mp4"
        )
        val validVid1 = MediaItem(
            id = "valid_vid_1",
            title = "Valid Video 1",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            uriPath = "https://example.com/valid_1.mp4"
        )
        val validVid2 = MediaItem(
            id = "valid_vid_2",
            title = "Valid Video 2",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            uriPath = "https://example.com/valid_2.mp4"
        )
        val validPhoto = MediaItem(
            id = "valid_photo_1",
            title = "Valid Photo 1",
            mediaType = "PHOTO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            uriPath = "https://example.com/photo_1.jpg"
        )

        val realRepo = MediaRepository(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        realRepo.setMediaItemsForTesting(listOf(thumbnailFailedVid, corruptVid, validVid1, validVid2, validPhoto))

        // Verify Library visibility: THUMBNAIL_FAILED is visible in Library, CORRUPT is hidden
        assertTrue("Thumbnail failed video remains visible in Library", realRepo.isItemVisibleInLibrary(thumbnailFailedVid))
        assertFalse("Corrupt video must be excluded from Library", realRepo.isItemVisibleInLibrary(corruptVid))
        assertTrue("Valid video 1 must be visible in Library", realRepo.isItemVisibleInLibrary(validVid1))
        assertTrue("Valid video 2 must be visible in Library", realRepo.isItemVisibleInLibrary(validVid2))
        assertTrue("Valid photo must be visible in Library", realRepo.isItemVisibleInLibrary(validPhoto))

        // Simulate 5 consecutive Pull-to-Refreshes
        val sessionManager = DiscoverSessionManager()
        runBlocking {
            repeat(5) { refreshIndex ->
                val snapshot = sessionManager.generateSnapshot(
                    repository = realRepo,
                    tasteDNA = tasteDNA,
                    profile = TasteDNA.PreferenceProfile(),
                    stats = stats,
                    creatorProfiles = emptyMap(),
                    forceNewSession = true
                )

                val allSnapshotItems = snapshot.obsessions.flatMap { it.previewItems }
                assertFalse("Refresh #$refreshIndex must not include thumbnail failed video in Discover", allSnapshotItems.any { it.id == "tf_vid" })
                assertFalse("Refresh #$refreshIndex must not include corrupt video in Discover", allSnapshotItems.any { it.id == "corrupt_vid" })
                assertTrue("Refresh #$refreshIndex must retain valid candidates in Discover", allSnapshotItems.any { it.id in listOf("valid_vid_1", "valid_vid_2", "valid_photo_1") })
            }
        }
    }
}
