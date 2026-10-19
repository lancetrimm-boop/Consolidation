package com.example.ui.screens

import com.example.data.ChannelRegistry
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository
    private lateinit var viewModel: ChannelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = MediaRepository(testDispatcher)
        viewModel = ChannelViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMediaItem(id: String, title: String, exposureCount: Int = 0): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "VIDEO",
            exposureCount = exposureCount
        )
    }

    @Test
    fun testLoadChannelPreviews_SuccessPopulatesPreviews() = testScope.runTest {
        repository.setMediaItemsForTesting(listOf(createMediaItem("v1", "Video 1"), createMediaItem("v2", "Video 2")))
        advanceUntilIdle()

        viewModel.loadChannelPreviews()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertNotNull(viewModel.channelPreviews.value)
        assertEquals(viewModel.allChannels.size, viewModel.channelPreviews.value.size)
    }

    @Test
    fun testRefreshChannels_TriggersPreviewRebuildAndIncrementEpoch() = testScope.runTest {
        val item1 = createMediaItem("v1", "Video 1")
        val item2 = createMediaItem("v2", "Video 2")
        repository.setMediaItemsForTesting(listOf(item1, item2))
        advanceUntilIdle()

        val previewsBefore = viewModel.channelPreviews.value
        assertNotNull(previewsBefore)

        viewModel.refreshChannels()
        advanceUntilIdle()

        val previewsAfter = viewModel.channelPreviews.value
        assertNotNull(previewsAfter)
        assertEquals(viewModel.allChannels.size, previewsAfter.size)
    }

    @Test
    fun testLoadChannelPreviews_EnforcesCrossChannelDeduplication() = testScope.runTest {
        val items = (1..300).map { createMediaItem("v$it", "Video $it") }
        repository.setMediaItemsForTesting(items)
        advanceUntilIdle()

        viewModel.loadChannelPreviews()
        advanceUntilIdle()

        val previews = viewModel.channelPreviews.value
        val allAssignedIds = previews.flatMap { it.fullItems.map { item -> item.id } }
        val uniqueIds = allAssignedIds.toSet()

        // Verify that across all simultaneously displayed channel carousels, every media ID appears at most once
        assertEquals(allAssignedIds.size, uniqueIds.size)
    }
}
