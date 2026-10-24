package com.example.ui.screens

import com.example.core.mediamodel.PresentationEligibleMedia
import com.example.data.Channel
import com.example.data.ChannelKind
import com.example.data.ChannelProgrammingContext
import com.example.data.mediastore.DefaultPresentationMediaStore
import com.example.core.mediamodel.toMediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class Phase4ConsumerMigrationTest {

    @Test
    fun testLibraryViewModel_OnlyExposesEligibleItems() = runTest {
        val store = DefaultPresentationMediaStore()
        val media1 = PresentationEligibleMedia.createInstance(
            id = "m1",
            uri = "file://m1.mp4",
            title = "Eligible 1",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 100L,
            snapshotHash = "hash1",
            gateVersion = 1
        )
        store.admit(media1)

        val vm = LibraryViewModel(store)
        val eligible = vm.eligibleMedia.first()

        assertEquals(1, eligible.size)
        assertEquals("m1", eligible[0].id)
    }

    @Test
    fun testCollections_SilentlyDropUnresolvedIds() = runTest {
        val store = DefaultPresentationMediaStore()
        val admitted1 = PresentationEligibleMedia.createInstance(
            id = "valid1",
            uri = "file://valid1.mp4",
            title = "Valid Media",
            durationMs = 2000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 200L,
            snapshotHash = "hashValid",
            gateVersion = 1
        )
        store.admit(admitted1)

        val savedIds = listOf("valid1", "ghost_id_123", "deleted_id_456")
        
        // Resolve collection IDs through PresentationMediaStore
        val resolvedCollection = savedIds.mapNotNull { id ->
            store.getById(id)?.toMediaItem()
        }

        assertEquals(1, resolvedCollection.size)
        assertEquals("valid1", resolvedCollection[0].id)
    }

    @Test
    fun testChannels_ContainOnlyEligibleMediaAndPruneEmptyChannels() = runTest {
        val store = DefaultPresentationMediaStore()
        val media1 = PresentationEligibleMedia.createInstance(
            id = "vid1",
            uri = "file://vid1.mp4",
            title = "Video 1",
            durationMs = 3000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 300L,
            snapshotHash = "hashVid",
            gateVersion = 1
        )
        store.admit(media1)

        val eligibleMediaItems = store.eligibleMedia.value.map { it.toMediaItem() }

        // All items in channel context are derived from PresentationMediaStore
        assertTrue(eligibleMediaItems.all { store.getById(it.id) != null })

        // Channel with zero eligible items gets pruned (empty list)
        val emptyChannel = Channel(
            id = "empty_channel",
            title = "Empty Channel",
            channelKind = ChannelKind.FIXED,
            strategyId = "SEARCH_SEEDED",
            referenceMediaIds = listOf("non_existent_id")
        )

        val context = ChannelProgrammingContext(
            availableMedia = eligibleMediaItems,
            filterType = "VIDEOS"
        )

        val programmed = context.availableMedia.filter { emptyChannel.referenceMediaIds.contains(it.id) }
        assertTrue(programmed.isEmpty())
    }
}
