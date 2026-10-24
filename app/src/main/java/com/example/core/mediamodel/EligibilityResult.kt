package com.example.core.mediamodel

sealed interface EligibilityResult {
    data object Eligible : EligibilityResult

    data class Ineligible(val reasons: Set<IneligibilityReason>) : EligibilityResult {
        init {
            require(reasons.isNotEmpty()) { "Ineligible result must contain at least one failure reason." }
        }
    }
}
