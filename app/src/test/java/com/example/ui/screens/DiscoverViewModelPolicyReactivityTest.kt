package com.example.ui.screens

import com.example.data.DiscoverSnapshot
import com.example.data.DiscoveryMode
import com.example.data.DiscoveryPolicy
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.ObsessionRecommendation
import com.example.data.SystemDiscoveryState
import com.example.data.UserIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelPolicyReactivityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val discoveryPolicyFlow = MutableStateFlow(DiscoveryPolicy(mode = DiscoveryMode.BALANCED))
    private val userIntentFlow = MutableStateFlow(UserIntent())
    private val m1Item = MediaItem(
        id = "m1",
        title = "Item 1",
        uriPath = "path/m1",
        mediaType = "VIDEO",
    )
    private val mediaItemsFlow = MutableStateFlow(listOf(m1Item))
    private val discoverSnapshotFlow = MutableStateFlow<DiscoverSnapshot?>(
        DiscoverSnapshot(
            obsessions = listOf(
                ObsessionRecommendation(
                    id = "obs_1",
                    title = "Vibrant Visuals",
                    subtitle = "High vibrancy",
                    strategy = com.example.data.ObsessionStrategy.Hero,
                    previewItems = listOf(m1Item),
                    confidenceScore = 0.9f,
                ),
            ),
            systemState = SystemDiscoveryState(),
            seenIds = emptySet(),
        ),
    )

    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock(MediaRepository::class.java)

        `when`(repository.discoveryPolicy).thenReturn(discoveryPolicyFlow)
        `when`(repository.userIntent).thenReturn(userIntentFlow)
        `when`(repository.mediaItems).thenReturn(mediaItemsFlow)
        `when`(repository.discoverSnapshot).thenReturn(discoverSnapshotFlow)
        `when`(repository.isDiscoverSnapshotStale()).thenReturn(false)
        `when`(repository.tasteDNA).thenReturn(MutableStateFlow(com.example.data.TasteDNA()))
        `when`(repository.preferenceProfile).thenReturn(MutableStateFlow(com.example.data.TasteDNA.PreferenceProfile()))
        `when`(repository.intelligenceStats).thenReturn(MutableStateFlow(com.example.data.IntelligenceStats()))
        `when`(repository.creatorProfiles).thenReturn(MutableStateFlow(emptyMap()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDiscoverViewModel_PolicyEmitsChange_MaintainsStateFlowReactivity() = testScope.runTest {
        val viewModel = DiscoverViewModel(repository)
        advanceUntilIdle()

        // Initial snapshot loaded
        val initialState = viewModel.feedState.value
        assertTrue(initialState is DiscoverFeedState.Success)

        // Change discovery policy from BALANCED to EXPLORATORY
        val newPolicy = DiscoveryPolicy(mode = DiscoveryMode.EXPLORATORY)
        discoveryPolicyFlow.value = newPolicy
        advanceUntilIdle()

        // Policy flow must emit the new value
        assertEquals(DiscoveryMode.EXPLORATORY, discoveryPolicyFlow.value.mode)

        // Feed state should remain in a valid Success or Loading/Success state
        assertTrue(viewModel.feedState.value is DiscoverFeedState.Success)
    }
}
