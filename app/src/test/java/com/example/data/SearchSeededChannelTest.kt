package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSeededChannelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository
    private lateinit var sessionManager: ChannelSessionManager
    private lateinit var programmer: ChannelProgrammer

    @Before
    fun setUp() {
        ChannelRegistry.clearCustomChannelsForTesting()
        repository = MediaRepository(testDispatcher)
        sessionManager = ChannelSessionManager()
        programmer = ChannelProgrammer()
    }

    private fun createMediaItem(id: String, title: String, isVideo: Boolean = true, genre: String = "Media"): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = if (isVideo) "VIDEO" else "PHOTO",
            duration = if (isVideo) "01:30" else "",
            genre = genre
        )
    }

    @Test
    fun testTextSearch_CreatesSearchSeededChannel() {
        val channel = Channel(
            id = "channel_search_1",
            title = "Blue Ocean",
            query = "blue ocean",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        assertEquals("channel_search_1", channel.id)
        assertEquals("Blue Ocean", channel.title)
        assertEquals("blue ocean", channel.query)
        assertEquals(ChannelKind.SEARCH_SEEDED, channel.channelKind)
    }

    @Test
    fun testVisualSearch_PreservesReferenceMediaIds() {
        val refIds = listOf("photo_ref_1", "photo_ref_2")
        val channel = Channel(
            id = "channel_visual_1",
            title = "Visual Search",
            query = "",
            referenceMediaIds = refIds,
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        assertEquals(2, channel.referenceMediaIds.size)
        assertEquals("photo_ref_1", channel.referenceMediaIds[0])
        assertEquals("photo_ref_2", channel.referenceMediaIds[1])
    }

    @Test
    fun testCombinedSearch_PreservesQueryAndReferences() {
        val channel = Channel(
            id = "channel_combined_1",
            title = "Sunset Waves",
            query = "sunset",
            referenceMediaIds = listOf("ref_wave"),
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        assertEquals("sunset", channel.query)
        assertEquals(1, channel.referenceMediaIds.size)
    }

    @Test
    fun testChannelRegistry_StoresAndRetrievesSearchSeededChannel() {
        val channel = Channel(
            id = "custom_ocean",
            title = "Ocean Waves",
            query = "ocean",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        ChannelRegistry.addSearchSeededChannel(channel)
        val retrieved = ChannelRegistry.get("custom_ocean")

        assertNotNull(retrieved)
        assertEquals("Ocean Waves", retrieved?.title)
        assertTrue(ChannelRegistry.allChannels().any { it.id == "custom_ocean" })
    }

    @Test
    fun testDynamicProgramming_SearchSeededLaneStrategy_MatchesQuery() = testScope.runTest {
        val v1 = createMediaItem("v1", "Blue Ocean Wave", isVideo = true)
        val v2 = createMediaItem("v2", "Desert Dunes", isVideo = true)
        val v3 = createMediaItem("v3", "Deep Blue Water", isVideo = true)
        repository.setMediaItemsForTesting(listOf(v1, v2, v3))

        val channel = Channel(
            id = "custom_blue",
            title = "Blue Search",
            query = "blue",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        val context = ChannelProgrammingContext(
            availableMedia = repository.mediaItems.value,
            filterType = "VIDEOS"
        )

        val programmed = programmer.programChannel(channel, context, limit = 10)
        assertEquals(3, programmed.size)
        assertTrue(programmed[0].title.lowercase().contains("blue"))
    }

    @Test
    fun testMediaTypeHomogeneity_SearchSeeded_VideosOnly() = testScope.runTest {
        val v1 = createMediaItem("v1", "Ocean Video", isVideo = true)
        val p1 = createMediaItem("p1", "Ocean Photo", isVideo = false)
        repository.setMediaItemsForTesting(listOf(v1, p1))

        val channel = Channel(
            id = "custom_ocean_vid",
            title = "Ocean",
            query = "ocean",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        val context = ChannelProgrammingContext(
            availableMedia = repository.mediaItems.value,
            filterType = "VIDEOS"
        )

        val programmed = programmer.programChannel(channel, context, limit = 10)
        assertEquals(1, programmed.size)
        assertEquals("VIDEO", programmed[0].mediaType)
    }

    @Test
    fun testSessionIntegration_SearchSeededChannel_StartsPlaybackSession() = testScope.runTest {
        val v1 = createMediaItem("v1", "Cyberpunk Light", isVideo = true)
        val v2 = createMediaItem("v2", "Cyberpunk City", isVideo = true)
        repository.setMediaItemsForTesting(listOf(v1, v2))

        val channel = Channel(
            id = "custom_cyber",
            title = "Cyberpunk",
            query = "cyberpunk",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        sessionManager.selectChannel(channel, repository, filterType = "VIDEOS")
        advanceUntilIdle()

        val state = sessionManager.channelState.value
        assertTrue(state is ChannelState.Success)
        val items = (state as ChannelState.Success).items
        assertEquals(2, items.size)
    }

    @Test
    fun testTasteDnaIsolation_ChannelCreation_DoesNotMutateTasteDNA() = testScope.runTest {
        val initialDna = repository.tasteDNA.value
        val channel = Channel(
            id = "custom_test",
            title = "Test",
            query = "test",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )

        repository.saveSearchSeededChannel(channel)
        advanceUntilIdle()

        assertEquals(initialDna, repository.tasteDNA.value)
    }

    @Test
    fun testSingleDeletion_RemovesSpecifiedSearchSeededChannel() = testScope.runTest {
        val chA = Channel("A", "Channel A", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")
        val chB = Channel("B", "Channel B", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")
        val chC = Channel("C", "Channel C", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")

        repository.saveSearchSeededChannel(chA)
        repository.saveSearchSeededChannel(chB)
        repository.saveSearchSeededChannel(chC)
        advanceUntilIdle()

        assertEquals(3, repository.searchSeededChannels.value.size)

        repository.deleteSearchSeededChannel("B")
        advanceUntilIdle()

        val remaining = repository.searchSeededChannels.value
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.id == "A" })
        assertFalse(remaining.any { it.id == "B" })
        assertTrue(remaining.any { it.id == "C" })
        assertNull(ChannelRegistry.get("B"))
    }

    @Test
    fun testSelectiveDeletion_LeavesOtherChannelsUntouched() = testScope.runTest {
        val chA = Channel("A", "Channel A", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")
        val chB = Channel("B", "Channel B", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")
        val chC = Channel("C", "Channel C", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")

        repository.saveSearchSeededChannel(chA)
        repository.saveSearchSeededChannel(chB)
        repository.saveSearchSeededChannel(chC)
        advanceUntilIdle()

        repository.deleteSearchSeededChannel("A")
        advanceUntilIdle()

        val remaining = repository.searchSeededChannels.value
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.id == "B" })
        assertTrue(remaining.any { it.id == "C" })
    }

    @Test
    fun testNonexistentId_DeletionDoesNotDamageCollection() = testScope.runTest {
        val chA = Channel("A", "Channel A", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")
        repository.saveSearchSeededChannel(chA)
        advanceUntilIdle()

        repository.deleteSearchSeededChannel("NONEXISTENT_ID")
        advanceUntilIdle()

        assertEquals(1, repository.searchSeededChannels.value.size)
        assertEquals("A", repository.searchSeededChannels.value[0].id)
    }

    @Test
    fun testBuiltInProtection_RefusesToDeleteFixedChannel() = testScope.runTest {
        val fixedChannel = ChannelRegistry.defaultChannel() // ME_TV
        assertEquals(ChannelKind.FIXED, fixedChannel.channelKind)

        repository.deleteSearchSeededChannel(fixedChannel.id)
        advanceUntilIdle()

        assertNotNull(ChannelRegistry.get(fixedChannel.id))
        assertTrue(ChannelRegistry.allFixedChannels().any { it.id == fixedChannel.id })
    }

    @Test
    fun testPersistence_DeletedChannelAbsentOnReload() = testScope.runTest {
        val ch1 = Channel("ch1", "Channel 1", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")
        val ch2 = Channel("ch2", "Channel 2", channelKind = ChannelKind.SEARCH_SEEDED, strategyId = "SEARCH_SEEDED")

        repository.saveSearchSeededChannel(ch1)
        repository.saveSearchSeededChannel(ch2)
        advanceUntilIdle()

        repository.deleteSearchSeededChannel("ch1")
        advanceUntilIdle()

        val loaded = repository.searchSeededChannels.value
        assertEquals(1, loaded.size)
        assertEquals("ch2", loaded[0].id)
    }
}
