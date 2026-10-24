package com.example.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CollectionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMediaItem(id: String, title: String, genre: String = "Test"): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "PHOTO",
            genre = genre,
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testCollectionsScreen_RecentlyViewedCardIsAbsent_UserMediaCollectionsPresent() {
        val repository = mock<MediaRepository>()
        val screenshotItem = createMediaItem("aura_snapshot_01", "Aura_Frame_Snapshot", genre = "User Screenshots")
        val auraClipItem = createMediaItem("aura_clip_01", "Aura Export Clip", genre = "Aura Generated")
        val recentWatchedItem = createMediaItem("recent_01", "Watched Video")

        whenever(repository.watchHistory).thenReturn(MutableStateFlow(listOf(recentWatchedItem)))
        whenever(repository.observeVisualIndexReadiness()).thenReturn(flowOf())

        composeTestRule.setContent {
            CollectionsScreen(
                mediaItems = listOf(screenshotItem, auraClipItem, recentWatchedItem),
                repository = repository,
                onCollectionSelect = { _, _ -> },
                onFavoriteToggle = {},
                onLaunchAuraMoments = {},
                onSearch = {}
            )
        }

        // Verify User Screenshots & Aura Selected cards are present
        composeTestRule.onNodeWithText("User Screenshots").assertExists()
        composeTestRule.onNodeWithText("Aura Selected").assertExists()

        // Verify Recently Viewed smart collection is strictly ABSENT from Collections screen
        composeTestRule.onNodeWithText("Recently Viewed").assertDoesNotExist()
    }
}
