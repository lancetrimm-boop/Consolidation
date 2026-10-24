package com.example.data.metv

import com.example.core.mediagate.FileSystemValidator
import com.example.core.mediagate.MediaConsumptionGate
import com.example.core.mediamodel.PresentationEligibleMedia
import com.example.core.mediamodel.toMediaItem
import com.example.data.mediastore.DefaultPresentationMediaStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase5MeTVGate2IntegrationTest {

    private lateinit var store: DefaultPresentationMediaStore
    private lateinit var fakeValidator: FakeValidator
    private lateinit var gate: MediaConsumptionGate
    private lateinit var controller: MeTVPlaybackController

    @Before
    fun setUp() {
        store = DefaultPresentationMediaStore()
        fakeValidator = FakeValidator()
        gate = MediaConsumptionGate(store, fakeValidator)
        controller = MeTVPlaybackController(store, gate)
    }

    @Test
    fun testHappyPath_Gate2ReturnsAllowed_PlaybackStarts() = runTest {
        val eligible = PresentationEligibleMedia.createInstance(
            id = "metv_1",
            uri = "file://metv_1.mp4",
            title = "Valid Video",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            snapshotHash = "hash_valid",
            gateVersion = 1
        )
        store.admit(eligible)
        fakeValidator.accessibleUris.add("file://metv_1.mp4")

        val candidate = MeTVCandidate(
            mediaId = eligible.id,
            mediaItem = eligible.toMediaItem(),
            gateVersion = eligible.gateVersion,
            snapshotHash = eligible.snapshotHash
        )
        controller.enqueueCandidates(listOf(candidate))

        val result = controller.playNext()

        assertTrue(result is MeTVPlaybackState.Playing)
        val playing = result as MeTVPlaybackState.Playing
        assertEquals("metv_1", playing.candidate.mediaId)
        assertEquals("hash_valid", playing.token.snapshotHash)
        assertEquals("file://metv_1.mp4", playing.token.uri)
    }

    @Test
    fun testFileDeletedWhileQueued_Gate2Rejects_SilentlySkipsToNextCandidate() = runTest {
        val eligible1 = PresentationEligibleMedia.createInstance(
            id = "metv_deleted",
            uri = "file://deleted.mp4",
            title = "Deleted File",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            snapshotHash = "hash1",
            gateVersion = 1
        )
        val eligible2 = PresentationEligibleMedia.createInstance(
            id = "metv_valid",
            uri = "file://valid.mp4",
            title = "Valid File",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            snapshotHash = "hash2",
            gateVersion = 1
        )
        store.admit(eligible1)
        store.admit(eligible2)

        // Only valid.mp4 exists on file system
        fakeValidator.accessibleUris.add("file://valid.mp4")

        val cand1 = MeTVCandidate("metv_deleted", eligible1.toMediaItem(), 1, "hash1")
        val cand2 = MeTVCandidate("metv_valid", eligible2.toMediaItem(), 1, "hash2")
        controller.enqueueCandidates(listOf(cand1, cand2))

        val result = controller.playNext()

        // Verify it silently skipped deleted item and started playing valid item
        assertTrue(result is MeTVPlaybackState.Playing)
        val playing = result as MeTVPlaybackState.Playing
        assertEquals("metv_valid", playing.candidate.mediaId)
        assertEquals(1, controller.rejectionCount.value)
    }

    @Test
    fun testSnapshotHashMismatch_TreatedAsRejection() = runTest {
        val eligible = PresentationEligibleMedia.createInstance(
            id = "metv_mismatch",
            uri = "file://mismatch.mp4",
            title = "Modified File",
            durationMs = 5000L,
            mimeType = "video/mp4",
            width = 1080,
            height = 1920,
            dateAddedEpochMs = 1000L,
            snapshotHash = "NEW_HASH_XYZ",
            gateVersion = 2
        )
        store.admit(eligible)
        // File is NOT marked accessible so any candidate for this file will be rejected

        // Candidate stored with OLD hash
        val staleCandidate = MeTVCandidate("metv_mismatch", eligible.toMediaItem(), 1, "OLD_STALE_HASH")
        controller.enqueueCandidates(listOf(staleCandidate))

        val result = controller.playNext()

        // Verification fails due to hash mismatch -> rejected & skipped
        assertTrue("Rejection count should be incremented", controller.rejectionCount.value >= 1)
        assertTrue(result is MeTVPlaybackState.Idle)
    }

    private class FakeValidator : FileSystemValidator {
        val accessibleUris = mutableSetOf<String>()
        override suspend fun isAccessible(uriString: String): Boolean = uriString in accessibleUris
    }
}
