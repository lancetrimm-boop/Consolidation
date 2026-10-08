package com.example.core.mediagate

import com.example.core.mediamodel.ClassifiedMediaItem
import com.example.core.mediamodel.EligibilityResult
import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.PresentationEligibleMedia
import com.example.data.mediastore.DiagnosticMediaStore
import com.example.data.mediastore.IneligibleMediaEntry
import com.example.data.mediastore.PresentationMediaStore

interface MediaAdmissionGate {
    suspend fun processClassifiedItem(item: ClassifiedMediaItem)
    suspend fun reEvaluate(id: String)
}

interface ClassifiedItemProvider {
    suspend fun getClassifiedItem(id: String): ClassifiedMediaItem?
}

class DefaultMediaAdmissionGate(
    private val evaluator: MediaEligibilityEvaluator = MediaEligibilityEvaluator,
    private val presentationStore: PresentationMediaStore,
    private val diagnosticStore: DiagnosticMediaStore,
    private val hasher: SnapshotHasher = DefaultSnapshotHasher(),
    private val itemProvider: ClassifiedItemProvider
) : MediaAdmissionGate {

    override suspend fun processClassifiedItem(item: ClassifiedMediaItem) {
        val evaluation = evaluator.evaluate(item)
        when (evaluation) {
            is EligibilityResult.Eligible -> {
                val snapshotHash = hasher.compute(item)
                val eligibleMedia = PresentationEligibleMedia.createInstance(
                    id = item.id,
                    uri = item.uri,
                    title = item.title,
                    durationMs = item.durationMs,
                    mimeType = item.mimeType,
                    width = item.width,
                    height = item.height,
                    dateAddedEpochMs = item.dateAddedEpochMs,
                    snapshotHash = snapshotHash,
                    gateVersion = MediaEligibilityEvaluator.CURRENT_CONTRACT_VERSION
                )
                presentationStore.admit(eligibleMedia)
                diagnosticStore.clearDiagnostic(item.id)
                EligibilityMetricsTracker.recordGate1Admitted()
            }
            is EligibilityResult.Ineligible -> {
                presentationStore.revoke(item.id, evaluation.reasons)
                val entry = IneligibleMediaEntry(
                    id = item.id,
                    filePath = item.uri,
                    title = item.title,
                    sizeBytes = item.fileSizeBytes,
                    reasons = evaluation.reasons,
                    detectedAtEpochMs = System.currentTimeMillis()
                )
                diagnosticStore.recordIneligible(entry)
                EligibilityMetricsTracker.recordGate1Rejected(evaluation.reasons)
            }
        }
    }

    override suspend fun reEvaluate(id: String) {
        val item = itemProvider.getClassifiedItem(id)
        if (item == null) {
            presentationStore.revoke(id, setOf(IneligibilityReason.MISSING))
            diagnosticStore.clearDiagnostic(id)
        } else {
            processClassifiedItem(item)
        }
    }
}
