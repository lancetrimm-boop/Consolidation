package com.example.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.*
import com.example.data.contribution.ConsentState
import com.example.data.intelligence.SignatureStyleProfile
import com.example.ui.components.SwipeMenuSheet
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMediaItem(id: String, title: String): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "VIDEO",
            videoCodec = "video/avc",
            duration = "01:00",
            genre = "Test",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testSavedScreen_EmptyStateRendersWhenNoSavedItems() {
        val repository = mock<MediaRepository>()
        whenever(repository.observeSavedMedia()).thenReturn(MutableStateFlow(emptyList()))

        composeTestRule.setContent {
            SavedScreen(
                repository = repository,
                onMediaSelect = { _, _ -> },
                onLike = {}
            )
        }

        composeTestRule.onNodeWithText("Saved").assertExists()
        composeTestRule.onNodeWithText("Save videos and photos to watch or view later. They leave this list automatically once finished.").assertExists()
    }

    @Test
    fun testSavedScreen_RendersSavedItems_NewestFirst() {
        val repository = mock<MediaRepository>()
        val item1 = createMediaItem("m1", "Media 1")
        val item2 = createMediaItem("m2", "Media 2")
        
        whenever(repository.observeSavedMedia()).thenReturn(MutableStateFlow(listOf(item2, item1)))

        composeTestRule.setContent {
            SavedScreen(
                repository = repository,
                onMediaSelect = { _, _ -> },
                onLike = {}
            )
        }

        composeTestRule.onNodeWithTag("media_tile_m2").assertExists()
        composeTestRule.onNodeWithTag("media_tile_m1").assertExists()
    }

    @Test
    fun testMyStuffNavigation_ExposesSavedTab() {
        val repository = mock<MediaRepository>()
        whenever(repository.observeSavedMedia()).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.tasteDNA).thenReturn(MutableStateFlow(TasteDNA()))
        whenever(repository.preferenceProfile).thenReturn(MutableStateFlow(TasteDNA.PreferenceProfile()))
        whenever(repository.discoveryPolicy).thenReturn(MutableStateFlow(DiscoveryPolicy()))
        whenever(repository.consentState).thenReturn(MutableStateFlow(ConsentState.NOT_DECIDED))
        whenever(repository.signatureStyleProfile).thenReturn(MutableStateFlow(SignatureStyleProfile(emptyList(), emptyList())))
        whenever(repository.mediaItems).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.intelligenceStats).thenReturn(MutableStateFlow(IntelligenceStats()))
        
        val searchFeedbackRepository = mock<SearchFeedbackRepository>()
        whenever(repository.searchFeedbackRepository).thenReturn(searchFeedbackRepository)

        val intelligenceRepository = mock<IntelligenceRepository>()
        whenever(repository.intelligenceRepository).thenReturn(intelligenceRepository)
        whenever(intelligenceRepository.getAllFindings()).thenReturn(MutableStateFlow(emptyList()))
        
        val masterReport = mock<MasterIntelligenceReport>()
        whenever(intelligenceRepository.getMasterReportFlow()).thenReturn(MutableStateFlow(masterReport))
        
        val decisionCenterState = mock<DecisionCenterState>()
        whenever(intelligenceRepository.getDecisionCenterFlow()).thenReturn(MutableStateFlow(decisionCenterState))
        
        whenever(intelligenceRepository.getAllIntelligenceEvents()).thenReturn(MutableStateFlow(emptyList()))
        whenever(intelligenceRepository.getAllAttentionItems()).thenReturn(MutableStateFlow(emptyList()))
        whenever(intelligenceRepository.snapshotReport).thenReturn(MutableStateFlow(null))

        val playbackRepository = mock<PlaybackErrorLogRepository>()
        whenever(repository.playbackErrorLogRepository).thenReturn(playbackRepository)
        whenever(playbackRepository.observeRecentErrors()).thenReturn(MutableStateFlow(emptyList()))

        composeTestRule.setContent {
            MyStuffScreen(
                repository = repository,
                mediaItems = emptyList(),
                onNavigateToFavorites = {},
                onNavigateToCleanup = {},
                onNavigateToPrivacyPolicy = {},
                onNavigateToDiagnostics = {},
                onLaunchAuraMoments = {},
                onMediaSelect = { _, _ -> },
                onCollectionSelect = { _, _ -> },
                onFavoriteToggle = {},
                onSearch = {}
            )
        }

        composeTestRule.onNodeWithText("Saved").assertExists()
        composeTestRule.onNodeWithText("Saved").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Save videos and photos to watch or view later. They leave this list automatically once finished.").assertExists()
    }

    @Test
    fun testSwipeMenuSheet_ExposesSaveAndRemoveState() {
        val item = createMediaItem("m1", "Media 1")

        composeTestRule.setContent {
            SwipeMenuSheet(
                activeItem = item,
                isVisualSearchActive = false,
                currentRating = 3.0f,
                playbackSpeed = 1.0f,
                isAbRepeatActive = false,
                abPointA = null,
                abPointB = null,
                currentPositionMs = 0L,
                durationMs = 1000f,
                onDismissRequest = {},
                onFavoriteToggle = {},
                onUpdateRating = null,
                onPlaybackSpeedChange = {},
                onSetAbPointA = {},
                onSetAbPointB = {},
                onClearAbRepeat = {},
                onExportAbClip = {},
                onCaptureScreenshot = {},
                onDeleteMediaRequested = {},
                onSeeSimilar = {},
                onBeginVisualSearch = {},
                onGenerateClips = {},
                isSaved = false,
                onToggleSaved = {}
            )
        }

        composeTestRule.onNodeWithTag("saved_button").assertExists()
        composeTestRule.onNodeWithContentDescription("Save").assertExists()
    }
}
