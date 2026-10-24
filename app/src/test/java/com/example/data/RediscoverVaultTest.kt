package com.example.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RediscoverVaultTest {

    private lateinit var programmer: ChannelProgrammer
    private lateinit var rediscoverChannel: Channel

    @Before
    fun setUp() {
        programmer = ChannelProgrammer()
        rediscoverChannel = Channel(
            id = "REDISCOVER",
            title = "Rediscover Vault",
            query = "rediscover forgotten library discovery",
            channelKind = ChannelKind.FIXED,
            strategyId = "REDISCOVER"
        )
    }

    private fun createItem(id: String, isVid: Boolean = true, exposure: Int = 0, lastViewedAgoMs: Long? = null): MediaItem {
        val now = System.currentTimeMillis()
        val lastViewed = if (lastViewedAgoMs != null) now - lastViewedAgoMs else null
        return MediaItem(
            id = id,
            title = "Media $id",
            mediaType = if (isVid) "VIDEO" else "PHOTO",
            duration = if (isVid) "01:30" else "",
            genre = "General",
            lastViewedTimestamp = lastViewed
        )
    }

    @Test
    fun testRediscover_OnlyUnseenAvailable_InterleavesAndOutputsUnseenCandidates() {
        val items = (1..10).map { createItem("unseen_$it", exposure = 0) }
        val context = ChannelProgrammingContext(
            availableMedia = items,
            filterType = "VIDEOS"
        )

        val block = programmer.programChannel(rediscoverChannel, context, limit = 20)
        assertEquals(10, block.size)
        assertTrue(block.all { it.mediaType == "VIDEO" })
    }

    @Test
    fun testRediscover_OnlyForgottenAvailable_OutputsForgottenCandidates() {
        val thirty1Days = 31L * 24 * 60 * 60 * 1000
        val items = (1..10).map { createItem("forgotten_$it", exposure = 3, lastViewedAgoMs = thirty1Days + it * 1000000L) }
        val expMap = items.associate { it.id to 3 }

        val context = ChannelProgrammingContext(
            availableMedia = items,
            filterType = "VIDEOS",
            exposureMap = expMap
        )

        val block = programmer.programChannel(rediscoverChannel, context, limit = 20)
        assertEquals(10, block.size)
    }

    @Test
    fun testRediscover_BothUnseenAndForgottenAvailable_InterleavesBothPools() {
        val thirty1Days = 31L * 24 * 60 * 60 * 1000
        val unseen = (1..5).map { createItem("unseen_$it", exposure = 0) }
        val forgotten = (1..5).map { createItem("forgotten_$it", exposure = 2, lastViewedAgoMs = thirty1Days + it * 100000L) }

        val expMap = forgotten.associate { it.id to 2 }
        val allItems = unseen + forgotten

        val context = ChannelProgrammingContext(
            availableMedia = allItems,
            filterType = "VIDEOS",
            exposureMap = expMap
        )

        val block = programmer.programChannel(rediscoverChannel, context, limit = 20)
        assertEquals(10, block.size)

        // Confirm both Pools are present in block
        assertTrue(block.any { it.id.startsWith("unseen_") })
        assertTrue(block.any { it.id.startsWith("forgotten_") })
    }

    @Test
    fun testRediscover_StrictMediaTypeHomogeneity_Videos() {
        val unseenVid = createItem("v1", isVid = true, exposure = 0)
        val unseenPic = createItem("p1", isVid = false, exposure = 0)

        val context = ChannelProgrammingContext(
            availableMedia = listOf(unseenVid, unseenPic),
            filterType = "VIDEOS"
        )

        val block = programmer.programChannel(rediscoverChannel, context, limit = 20)
        assertEquals(1, block.size)
        assertEquals("v1", block[0].id)
    }

    @Test
    fun testRediscover_NoDuplicateItemsInBlock() {
        val thirty1Days = 31L * 24 * 60 * 60 * 1000
        val item1 = createItem("item_1", exposure = 1, lastViewedAgoMs = thirty1Days * 2)
        val item2 = createItem("item_2", exposure = 0)

        val context = ChannelProgrammingContext(
            availableMedia = listOf(item1, item2),
            filterType = "VIDEOS",
            exposureMap = mapOf("item_1" to 1)
        )

        val block = programmer.programChannel(rediscoverChannel, context, limit = 20)
        val uniqueIds = block.map { it.id }.toSet()
        assertEquals(block.size, uniqueIds.size)
    }
}
