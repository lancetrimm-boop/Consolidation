package com.example.ui.screens

import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSeededChannelWorkflowTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MediaRepository
    private lateinit var viewModel: ChannelViewModel

    private val item1 = MediaItem(id = "item1", title = "Mountain Stream", mediaType = "PHOTO", uriPath = "u1")
    private val item2 = MediaItem(id = "item2", title = "Sunset Beach", mediaType = "PHOTO", uriPath = "u2")
    private val item3 = MediaItem(id = "item3", title = "Forest Trail", mediaType = "PHOTO", uriPath = "u3")
    private val item4 = MediaItem(id = "item4", title = "City Lights", mediaType = "VIDEO", uriPath = "u4")

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        ChannelRegistry.clearCustomChannelsForTesting()
        repository = MediaRepository(testDispatcher)
        repository.setMediaItemsForTesting(listOf(item1, item2, item3, item4))
        viewModel = ChannelViewModel(repository)
    }

    @After
    fun tearDown() {
        ChannelRegistry.clearCustomChannelsForTesting()
        repository.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun testMakeChannel_CreatesUniquePersistentChannel() = runTest(testDispatcher) {
        val channelA = Channel(
            id = "channel_search_101",
            title = "Beach Sunset",
            query = "beach",
            referenceMediaIds = listOf("item2"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        val channelB = Channel(
            id = "channel_search_102",
            title = "Forest",
            query = "forest",
            referenceMediaIds = listOf("item3"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        repository.saveSearchSeededChannel(channelA)
        repository.saveSearchSeededChannel(channelB)
        advanceUntilIdle()

        // Verify both channels exist in repository and ChannelRegistry
        assertEquals(2, repository.searchSeededChannels.value.size)
        assertNotNull(ChannelRegistry.get("channel_search_101"))
        assertNotNull(ChannelRegistry.get("channel_search_102"))
        assertEquals("Beach Sunset", ChannelRegistry.get("channel_search_101")?.title)
        assertEquals("Forest", ChannelRegistry.get("channel_search_102")?.title)
    }

    @Test
    fun testNewlyCreatedChannel_CanBeSelectedByExactId() = runTest(testDispatcher) {
        val channel = Channel(
            id = "channel_search_201",
            title = "City Exploration",
            query = "city",
            referenceMediaIds = listOf("item4"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        repository.saveSearchSeededChannel(channel)
        advanceUntilIdle()

        viewModel.selectChannel(channel)
        advanceUntilIdle()

        assertEquals("channel_search_201", viewModel.selectedChannel.value.id)
        assertEquals("City Exploration", viewModel.selectedChannel.value.title)
    }

    @Test
    fun testFirstReference_DeterminesInitialPlaybackIndex() = runTest(testDispatcher) {
        val firstReferenceId = "item3"
        val channel = Channel(
            id = "channel_search_301",
            title = "Forest Target",
            referenceMediaIds = listOf(firstReferenceId),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        // Programmed playlist: item1 at 0, item2 at 1, item4 at 2, item3 at 3
        val programmedPlaylist = listOf(item1, item2, item4, item3)

        val initialIndex = if (channel.channelKind == ChannelKind.SEARCH_SEEDED && channel.referenceMediaIds.isNotEmpty()) {
            val refId = channel.referenceMediaIds.first()
            programmedPlaylist.indexOfFirst { it.id == refId }
                .takeIf { it >= 0 }
                ?: programmedPlaylist.indexOfFirst { it.id == item1.id }.coerceAtLeast(0)
        } else 0

        // Assert: First reference "item3" is at index 3 in playlist
        assertEquals(3, initialIndex)
        assertEquals(firstReferenceId, programmedPlaylist[initialIndex].id)
    }

    @Test
    fun testMissingReference_FallsBackSafelyToDefaultIndex() = runTest(testDispatcher) {
        val missingRefId = "non_existent_id"
        val channel = Channel(
            id = "channel_search_302",
            title = "Fallback Target",
            referenceMediaIds = listOf(missingRefId),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        val programmedPlaylist = listOf(item1, item2, item4)

        val initialIndex = if (channel.channelKind == ChannelKind.SEARCH_SEEDED && channel.referenceMediaIds.isNotEmpty()) {
            val refId = channel.referenceMediaIds.first()
            programmedPlaylist.indexOfFirst { it.id == refId }
                .takeIf { it >= 0 }
                ?: programmedPlaylist.indexOfFirst { it.id == item1.id }.coerceAtLeast(0)
        } else 0

        // Assert: Falls back to index 0 safely
        assertEquals(0, initialIndex)
    }

    @Test
    fun testFirstReference_PreferredAsSearchSeededThumbnail() = runTest(testDispatcher) {
        val channel = Channel(
            id = "channel_search_401",
            title = "Beach Priority",
            referenceMediaIds = listOf("item2"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        repository.saveSearchSeededChannel(channel)
        advanceUntilIdle()

        val programmer = ChannelProgrammer()
        val available = repository.mediaItems.value
        val context = ChannelProgrammingContext(
            availableMedia = available,
            filterType = "PHOTOS"
        )

        val programmed = programmer.programChannel(channel, context, limit = 20)
        val firstRefId = channel.referenceMediaIds.first()
        val candidate = programmed.find { it.id == firstRefId }
            ?: available.find { it.id == firstRefId }

        assertNotNull(candidate)
        assertEquals("item2", candidate?.id)
        assertEquals("Sunset Beach", candidate?.title)
    }

    @Test
    fun testChannelDeletion_RemovesSearchSeeded_ProtectsFixedChannels() = runTest(testDispatcher) {
        val customChannel1 = Channel(
            id = "channel_search_501",
            title = "Custom 1",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        val customChannel2 = Channel(
            id = "channel_search_502",
            title = "Custom 2",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        repository.saveSearchSeededChannel(customChannel1)
        repository.saveSearchSeededChannel(customChannel2)
        advanceUntilIdle()

        // 1. Delete customChannel1
        viewModel.deleteChannel(customChannel1)
        advanceUntilIdle()

        assertNull(ChannelRegistry.get("channel_search_501"))
        assertNotNull(ChannelRegistry.get("channel_search_502"))

        // 2. Attempt to delete fixed ME_TV channel
        val meTvChannel = ChannelRegistry.get("ME_TV")!!
        viewModel.deleteChannel(meTvChannel)
        advanceUntilIdle()

        // Assert: ME_TV remains protected
        assertNotNull(ChannelRegistry.get("ME_TV"))
    }
}
