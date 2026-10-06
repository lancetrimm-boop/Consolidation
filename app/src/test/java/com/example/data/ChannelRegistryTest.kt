package com.example.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelRegistryTest {

    private val expectedIds = listOf(
        "ME_TV",
        "FAVORITES",
        "REDISCOVER",
        "CONTINUE",
        "MOOD"
    )

    private val removedIds = listOf(
        "ERA",
        "DAYPARTS",
        "SEASONAL_POPUPS",
        "SCOPED_RANDOM",
        "NOVELTY"
    )

    @Before
    fun setUp() {
        ChannelRegistry.clearCustomChannelsForTesting()
    }

    @Test
    fun testExactly5FixedChannelsExist() {
        val channels = ChannelRegistry.allFixedChannels()
        assertEquals(5, channels.size)
    }

    @Test
    fun testAll5ChannelIdsAreUnique() {
        val channels = ChannelRegistry.allFixedChannels()
        val ids = channels.map { it.id }
        assertEquals(5, ids.toSet().size)
    }

    @Test
    fun testMeTvChannelExistsAndIsDefault() {
        val meTv = ChannelRegistry.get("ME_TV")
        assertNotNull(meTv)
        assertEquals("Me TV", meTv?.title)
        assertTrue(meTv!!.isDefault)
        assertEquals(meTv, ChannelRegistry.defaultChannel())
    }

    @Test
    fun testFixedChannelOrderingIsDeterministic() {
        val channels = ChannelRegistry.allFixedChannels()
        val ids = channels.map { it.id }
        assertEquals(expectedIds, ids)

        channels.forEachIndexed { index, channel ->
            assertEquals(index, channel.order)
        }
    }

    @Test
    fun testEverySurvivingChannelCanBeRetrievedById() {
        expectedIds.forEach { id ->
            val channel = ChannelRegistry.get(id)
            assertNotNull("Failed to retrieve channel $id", channel)
            assertEquals(id, channel?.id)
        }
    }

    @Test
    fun testRemovedChannelsAreNotReturnedInFixedRegistry() {
        removedIds.forEach { id ->
            assertNull("Removed channel $id should not exist in fixed registry", ChannelRegistry.get(id))
        }
    }

    @Test
    fun testUnknownChannelLookupReturnsNull() {
        val unknown = ChannelRegistry.get("UNKNOWN_CHANNEL_ID")
        assertNull(unknown)
    }

    @Test
    fun testEveryFixedChannelHasMatchingStrategyId() {
        expectedIds.forEach { id ->
            val channel = ChannelRegistry.get(id)
            assertNotNull(channel)
            assertEquals(id, channel?.strategyId)
        }
    }

    @Test
    fun testEveryFixedChannelIsMarkedForProGating() {
        val channels = ChannelRegistry.allFixedChannels()
        assertTrue(channels.all { it.isProGated })
    }

    @Test
    fun testSearchSeededChannelSupport_DoesNotAlterFixedRegistry() {
        val seededChannel = Channel(
            id = "seeded_123",
            title = "Search Seed",
            query = "sunset beaches",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED",
            order = -1,
            isDefault = false,
            isProGated = true
        )

        assertEquals(ChannelKind.SEARCH_SEEDED, seededChannel.channelKind)
        // Fixed registry remains strictly 5 items
        assertEquals(5, ChannelRegistry.allFixedChannels().size)
        assertNull(ChannelRegistry.get("seeded_123"))

        ChannelRegistry.addSearchSeededChannel(seededChannel)
        assertNotNull(ChannelRegistry.get("seeded_123"))
        assertEquals(6, ChannelRegistry.allChannels().size)
    }
}
