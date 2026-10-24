package com.example.core.mediagate

import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.PlaybackConsumptionToken
import com.example.data.mediastore.PresentationMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaConsumptionGate(
    private val presentationMediaStore: PresentationMediaStore,
    private val fileSystemValidator: FileSystemValidator
) {
    sealed interface ConsumptionCheckResult {
        data class Allowed(val token: PlaybackConsumptionToken) : ConsumptionCheckResult
        data class Rejected(val reasons: Set<IneligibilityReason>) : ConsumptionCheckResult
    }

    /**
     * T-0 re-validation. Must run on Dispatchers.IO.
     */
    suspend fun verifyForPlayback(
        candidateId: String,
        expectedSnapshotHash: String?
    ): ConsumptionCheckResult = withContext(Dispatchers.IO) {
        val media = presentationMediaStore.getById(candidateId)
            ?: return@withContext ConsumptionCheckResult.Rejected(setOf(IneligibilityReason.MISSING))

        if (expectedSnapshotHash != null && media.snapshotHash != expectedSnapshotHash) {
            return@withContext ConsumptionCheckResult.Rejected(setOf(IneligibilityReason.UNTESTED))
        }

        val accessible = fileSystemValidator.isAccessible(media.uri)
        if (!accessible) {
            return@withContext ConsumptionCheckResult.Rejected(setOf(IneligibilityReason.INACCESSIBLE))
        }

        val token = PlaybackConsumptionToken(
            mediaId = media.id,
            uri = media.uri,
            validatedTimestampEpochMs = System.currentTimeMillis(),
            snapshotHash = media.snapshotHash
        )
        ConsumptionCheckResult.Allowed(token)
    }
}
