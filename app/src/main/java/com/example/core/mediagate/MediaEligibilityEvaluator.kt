package com.example.core.mediagate

import com.example.core.mediamodel.EligibilityResult
import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.TestStatus
import com.example.core.mediamodel.ClassifiedMediaItem

object MediaEligibilityEvaluator {
    const val CURRENT_CONTRACT_VERSION = 1

    /**
     * Pure, deterministic evaluation.
     * Must return ALL applicable reasons, never just the first one.
     */
    fun evaluate(item: ClassifiedMediaItem): EligibilityResult {
        val reasons = mutableSetOf<IneligibilityReason>()
        if (!item.isAnalyzed) {
            reasons.add(IneligibilityReason.ANALYSIS_PENDING)
        }
        if (item.testStatus == TestStatus.UNTESTED) {
            reasons.add(IneligibilityReason.UNTESTED)
        }
        if (!item.isCodecSupported) {
            reasons.add(IneligibilityReason.UNSUPPORTED_CODEC)
        }
        if (item.isCorrupt) {
            reasons.add(IneligibilityReason.CORRUPT)
        }
        if (!item.fileExists) {
            reasons.add(IneligibilityReason.MISSING)
        }
        if (!item.hasReadPermission) {
            reasons.add(IneligibilityReason.INACCESSIBLE)
        }

        return if (reasons.isEmpty()) {
            EligibilityResult.Eligible
        } else {
            EligibilityResult.Ineligible(reasons)
        }
    }
}
