package com.example.data.metv

import com.example.core.mediagate.MediaConsumptionGate
import com.example.core.mediamodel.PlaybackConsumptionToken
import com.example.core.mediamodel.toMediaItem
import com.example.data.mediastore.PresentationMediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

sealed interface MeTVPlaybackState {
    object Idle : MeTVPlaybackState
    data class Playing(
        val candidate: MeTVCandidate,
        val token: PlaybackConsumptionToken
    ) : MeTVPlaybackState
    data class Error(val message: String) : MeTVPlaybackState
}

class MeTVPlaybackController(
    private val presentationMediaStore: PresentationMediaStore,
    private val consumptionGate: MediaConsumptionGate
) {
    private val queue = ConcurrentLinkedQueue<MeTVCandidate>()
    private val _playbackState = MutableStateFlow<MeTVPlaybackState>(MeTVPlaybackState.Idle)
    val playbackState: StateFlow<MeTVPlaybackState> = _playbackState.asStateFlow()

    private val _rejectionCount = MutableStateFlow(0)
    val rejectionCount: StateFlow<Int> = _rejectionCount.asStateFlow()

    val currentQueue: List<MeTVCandidate>
        get() = queue.toList()

    fun enqueueCandidates(candidates: List<MeTVCandidate>) {
        val existingIds = queue.map { it.mediaId }.toSet()
        for (candidate in candidates) {
            if (candidate.mediaId !in existingIds) {
                queue.add(candidate)
            }
        }
    }

    fun removeCandidate(mediaId: String) {
        queue.removeIf { it.mediaId == mediaId }
    }

    suspend fun playNext(): MeTVPlaybackState {
        while (queue.isNotEmpty()) {
            val candidate = queue.poll() ?: break
            val check = consumptionGate.verifyForPlayback(
                candidateId = candidate.mediaId,
                expectedSnapshotHash = candidate.snapshotHash.ifEmpty { null }
            )

            when (check) {
                is MediaConsumptionGate.ConsumptionCheckResult.Allowed -> {
                    val state = MeTVPlaybackState.Playing(candidate, check.token)
                    _playbackState.value = state
                    return state
                }
                is MediaConsumptionGate.ConsumptionCheckResult.Rejected -> {
                    // Record metric & log rejection
                    _rejectionCount.value++
                    com.example.core.mediagate.EligibilityMetricsTracker.recordGate2Rejected()
                    presentationMediaStore.revoke(candidate.mediaId, check.reasons)
                    // Candidate removed from queue by poll() above
                    // Replenish queue and immediately try next candidate silently
                    replenishQueue()
                }
            }
        }

        // Silent replenishment retry
        replenishQueue()
        if (queue.isNotEmpty()) {
            val candidate = queue.poll()
            if (candidate != null) {
                val check = consumptionGate.verifyForPlayback(
                    candidateId = candidate.mediaId,
                    expectedSnapshotHash = candidate.snapshotHash.ifEmpty { null }
                )
                if (check is MediaConsumptionGate.ConsumptionCheckResult.Allowed) {
                    val state = MeTVPlaybackState.Playing(candidate, check.token)
                    _playbackState.value = state
                    return state
                } else if (check is MediaConsumptionGate.ConsumptionCheckResult.Rejected) {
                    _rejectionCount.value++
                    com.example.core.mediagate.EligibilityMetricsTracker.recordGate2Rejected()
                    presentationMediaStore.revoke(candidate.mediaId, check.reasons)
                }
            }
        }

        val idleState = MeTVPlaybackState.Idle
        _playbackState.value = idleState
        return idleState
    }

    fun replenishQueue() {
        val existingIds = queue.map { it.mediaId }.toSet()
        val eligibleList = presentationMediaStore.eligibleMedia.value
        val newCandidates = eligibleList
            .filter { it.id !in existingIds }
            .map { eligible ->
                MeTVCandidate(
                    mediaId = eligible.id,
                    mediaItem = eligible.toMediaItem(),
                    gateVersion = eligible.gateVersion,
                    snapshotHash = eligible.snapshotHash
                )
            }

        for (cand in newCandidates) {
            if (queue.map { it.mediaId }.none { it == cand.mediaId }) {
                queue.add(cand)
            }
        }
    }
}
