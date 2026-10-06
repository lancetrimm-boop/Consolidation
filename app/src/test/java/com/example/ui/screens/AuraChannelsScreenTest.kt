package com.example.ui.screens

import com.example.data.ChannelRegistry
import com.example.data.ChannelState
import com.example.data.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuraChannelsScreenTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository
    private lateinit var viewModel: ChannelViewModel

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
        viewModel = ChannelViewModel(repository)
    }

    @Test
    fun testViewModel_ObtainsLineupFromChannelRegistry() {
        val channels = viewModel.defaultChannels
        assertEquals(5, channels.size)
        assertEquals(ChannelRegistry.allFixedChannels(), channels)
    }

    @Test
    fun testViewModel_MeTvAppearsFirstAndIsDefault() {
        val firstChannel = viewModel.defaultChannels.first()
        assertEquals("ME_TV", firstChannel.id)
        assertEquals("Me TV", firstChannel.title)
        assertTrue(firstChannel.isDefault)
        assertEquals("ME_TV", viewModel.selectedChannel.value.id)
    }

    @Test
    fun testViewModel_SelectChannel_UpdatesSelectedChannelAndState() = testScope.runTest {
        val favoritesChannel = ChannelRegistry.get("FAVORITES")!!
        viewModel.selectChannel(favoritesChannel)
        advanceUntilIdle()

        assertEquals("FAVORITES", viewModel.selectedChannel.value.id)
        assertTrue(viewModel.channelState.value is ChannelState.Success || viewModel.channelState.value is ChannelState.Loading)
    }

    @Test
    fun testViewModel_SetFilterType_PreservesChannelSelection() = testScope.runTest {
        val favChannel = ChannelRegistry.get("FAVORITES")!!
        viewModel.selectChannel(favChannel)
        advanceUntilIdle()

        viewModel.setFilterType("PHOTOS")
        advanceUntilIdle()

        assertEquals("PHOTOS", viewModel.selectedFilterType.value)
        assertEquals("FAVORITES", viewModel.selectedChannel.value.id)
    }

    @Test
    fun testViewModel_EmptyLibrary_ProducesSuccessWithEmptyItems() = testScope.runTest {
        repository.setMediaItemsForTesting(emptyList())
        viewModel.refreshActiveChannel()
        advanceUntilIdle()

        val state = viewModel.channelState.value
        assertTrue(state is ChannelState.Success)
        assertEquals(0, (state as ChannelState.Success).items.size)
    }
}
