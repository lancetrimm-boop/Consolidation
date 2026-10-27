package com.example.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.*
import com.example.data.contribution.ConsentState
import com.example.data.intelligence.SignatureStyleProfile
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
class MyStuffScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setupMockRepository(): MediaRepository {
        val repository = mock<MediaRepository>()
        whenever(repository.observeSavedMedia()).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.watchHistory).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.observeVisualIndexReadiness()).thenReturn(flowOf())
        whenever(repository.tasteDNA).thenReturn(MutableStateFlow(TasteDNA()))
        whenever(repository.preferenceProfile).thenReturn(MutableStateFlow(TasteDNA.PreferenceProfile()))
        whenever(repository.discoveryPolicy).thenReturn(MutableStateFlow(DiscoveryPolicy()))
        whenever(repository.consentState).thenReturn(MutableStateFlow(ConsentState.NOT_DECIDED))
        whenever(repository.signatureStyleProfile).thenReturn(MutableStateFlow(SignatureStyleProfile(emptyList(), emptyList())))
        whenever(repository.mediaItems).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.intelligenceStats).thenReturn(MutableStateFlow(IntelligenceStats()))
        whenever(repository.scanProgress).thenReturn(MutableStateFlow(ScanProgressState()))
        
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

        return repository
    }

    @Test
    fun testMyStuffNavigation_ContainsAllFourTabs() {
        val repository = setupMockRepository()

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

        // Verify four expected tabs are present
        composeTestRule.onNodeWithText("Profile").assertExists()
        composeTestRule.onNodeWithText("Collections").assertExists()
        composeTestRule.onNodeWithText("Saved").assertExists()
        composeTestRule.onNodeWithText("Recently Watched").assertExists()
    }

    @Test
    fun testMyStuffNavigation_SelectingRecentlyWatchedRendersRecentlyViewedScreen() {
        val repository = setupMockRepository()

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

        // Tap Recently Watched
        composeTestRule.onNodeWithText("Recently Watched").performClick()
        composeTestRule.waitForIdle()

        // Assert empty state copy from RecentlyViewedScreen
        composeTestRule.onNodeWithText("Recently Viewed").assertExists()
        composeTestRule.onNodeWithText("No recently viewed media. Videos and photos you play or view will appear here.").assertExists()
    }
}
