package com.example.core.mediagate

import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.PresentationEligibleMedia
import com.example.data.mediastore.DefaultPresentationMediaStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MediaConsumptionGateTest {

    @Test
    fun testConsumptionGate_AllowedWhenValid() = runTest {
        val store = DefaultPresentationMediaStore()
        val media = PresentationEligibleMedia.createInstance(
            id = "m1",
            uri = "file://test.mp4",
            title = "Test",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 100L,
            snapshotHash = "abc123hash",
            gateVersion = 1
        )
        store.admit(media)

        val validator = object : FileSystemValidator {
            override suspend fun isAccessible(uriString: String): Boolean = true
        }

        val gate = MediaConsumptionGate(store, validator)
        val result = gate.verifyForPlayback("m1", "abc123hash")

        assertTrue(result is MediaConsumptionGate.ConsumptionCheckResult.Allowed)
        val allowed = result as MediaConsumptionGate.ConsumptionCheckResult.Allowed
        assertEquals("m1", allowed.token.mediaId)
        assertTrue(allowed.token.isFresh)
    }

    @Test
    fun testConsumptionGate_RejectedWhenMissing() = runTest {
        val store = DefaultPresentationMediaStore()
        val validator = object : FileSystemValidator {
            override suspend fun isAccessible(uriString: String): Boolean = true
        }

        val gate = MediaConsumptionGate(store, validator)
        val result = gate.verifyForPlayback("nonexistent", null)

        assertTrue(result is MediaConsumptionGate.ConsumptionCheckResult.Rejected)
        val rejected = result as MediaConsumptionGate.ConsumptionCheckResult.Rejected
        assertTrue(rejected.reasons.contains(IneligibilityReason.MISSING))
    }

    @Test
    fun testConsumptionGate_RejectedWhenHashMismatch() = runTest {
        val store = DefaultPresentationMediaStore()
        val media = PresentationEligibleMedia.createInstance(
            id = "m1",
            uri = "file://test.mp4",
            title = "Test",
            durationMs = 1000L,
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            dateAddedEpochMs = 100L,
            snapshotHash = "abc123hash",
            gateVersion = 1
        )
        store.admit(media)

        val validator = object : FileSystemValidator {
            override suspend fun isAccessible(uriString: String): Boolean = true
        }

        val gate = MediaConsumptionGate(store, validator)
        val result = gate.verifyForPlayback("m1", "wronghash")

        assertTrue(result is MediaConsumptionGate.ConsumptionCheckResult.Rejected)
        val rejected = result as MediaConsumptionGate.ConsumptionCheckResult.Rejected
        assertTrue(rejected.reasons.contains(IneligibilityReason.UNTESTED))
    }
}
