package com.example.core.mediagate

import android.util.Log
import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.PresentationEligibleMedia
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object EligibilityMetricsTracker {
    private val gate1Admitted = AtomicLong(0)
    private val gate1Rejected = AtomicLong(0)
    private val gate2Rejected = AtomicLong(0)
    private val batchRevokeCount = AtomicLong(0)

    private val gate1RejectionsByReason = ConcurrentHashMap<IneligibilityReason, AtomicLong>()

    fun recordGate1Admitted() {
        gate1Admitted.incrementAndGet()
    }

    fun recordGate1Rejected(reasons: Set<IneligibilityReason>) {
        gate1Rejected.incrementAndGet()
        for (reason in reasons) {
            gate1RejectionsByReason.getOrPut(reason) { AtomicLong(0) }.incrementAndGet()
        }
    }

    fun recordGate2Rejected() {
        gate2Rejected.incrementAndGet()
    }

    fun recordBatchRevoke(count: Int) {
        batchRevokeCount.addAndGet(count.toLong())
    }

    fun getGate1AdmittedCount(): Long = gate1Admitted.get()
    fun getGate1RejectedCount(): Long = gate1Rejected.get()
    fun getGate2RejectedCount(): Long = gate2Rejected.get()
    fun getBatchRevokeCount(): Long = batchRevokeCount.get()

    fun resetForTesting() {
        gate1Admitted.set(0)
        gate1Rejected.set(0)
        gate2Rejected.set(0)
        batchRevokeCount.set(0)
        gate1RejectionsByReason.clear()
    }

    /**
     * Debug assertion / sanity check to verify PresentationEligibleMedia integrity.
     */
    fun assertValidPresentationMedia(media: PresentationEligibleMedia, isDebug: Boolean = true) {
        if (isDebug) {
            check(media.id.isNotEmpty()) { "PresentationEligibleMedia ID must not be empty" }
            check(media.uri.isNotEmpty()) { "PresentationEligibleMedia URI must not be empty" }
            check(media.gateVersion > 0) { "PresentationEligibleMedia gateVersion must be > 0" }
            check(media.snapshotHash.isNotEmpty()) { "PresentationEligibleMedia snapshotHash must not be empty" }
        }
    }

    /**
     * Beta Leak Detector: Verifies that raw media IDs are not leaked directly into presentation layer.
     */
    fun verifyNoRawMediaLeak(mediaId: String, knownRawIds: Set<String>, isBeta: Boolean = true) {
        if (isBeta && knownRawIds.contains(mediaId)) {
            Log.w("LeakDetector", "[LEAK_WARNING] Raw media ID $mediaId observed in presentation flow!")
        }
    }
}
