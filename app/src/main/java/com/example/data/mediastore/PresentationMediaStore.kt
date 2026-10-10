package com.example.data.mediastore

import com.example.core.mediamodel.IneligibilityReason
import com.example.core.mediamodel.PresentationEligibleMedia
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class AdmissionProgress(
    val totalScanned: Int = 0,
    val totalAdmitted: Int = 0,
    val isAnalysisComplete: Boolean = false
)

interface PresentationMediaStore {
    val eligibleMedia: StateFlow<List<PresentationEligibleMedia>>
    val admissionProgress: StateFlow<AdmissionProgress>

    fun getById(id: String): PresentationEligibleMedia?

    fun admit(item: PresentationEligibleMedia)
    fun batchAdmit(items: List<PresentationEligibleMedia>)
    fun revoke(id: String, reasons: Set<IneligibilityReason>)
    fun batchRevoke(ids: Set<String>, reasons: Set<IneligibilityReason>)
    fun updateProgress(totalScanned: Int, totalAdmitted: Int, isAnalysisComplete: Boolean)
    fun markAnalysisComplete()
}

class DefaultPresentationMediaStore : PresentationMediaStore {
    private val store = ConcurrentHashMap<String, PresentationEligibleMedia>()
    private val _eligibleMedia = MutableStateFlow<List<PresentationEligibleMedia>>(emptyList())
    override val eligibleMedia: StateFlow<List<PresentationEligibleMedia>> = _eligibleMedia.asStateFlow()

    private val _admissionProgress = MutableStateFlow(AdmissionProgress())
    override val admissionProgress: StateFlow<AdmissionProgress> = _admissionProgress.asStateFlow()

    override fun getById(id: String): PresentationEligibleMedia? {
        return store[id]
    }

    override fun admit(item: PresentationEligibleMedia) {
        store[item.id] = item
        updateFlow()
    }

    override fun batchAdmit(items: List<PresentationEligibleMedia>) {
        if (items.isEmpty()) return
        for (item in items) {
            store[item.id] = item
        }
        updateFlow()
    }

    override fun revoke(id: String, reasons: Set<IneligibilityReason>) {
        if (store.remove(id) != null) {
            updateFlow()
        }
    }

    override fun batchRevoke(ids: Set<String>, reasons: Set<IneligibilityReason>) {
        if (ids.isEmpty()) return
        var removedCount = 0
        for (id in ids) {
            if (store.remove(id) != null) {
                removedCount++
            }
        }
        if (removedCount > 0) {
            com.example.core.mediagate.EligibilityMetricsTracker.recordBatchRevoke(removedCount)
            updateFlow()
        }
    }

    override fun updateProgress(totalScanned: Int, totalAdmitted: Int, isAnalysisComplete: Boolean) {
        _admissionProgress.value = AdmissionProgress(
            totalScanned = totalScanned,
            totalAdmitted = totalAdmitted,
            isAnalysisComplete = isAnalysisComplete
        )
    }

    override fun markAnalysisComplete() {
        _admissionProgress.value = _admissionProgress.value.copy(
            totalAdmitted = store.size,
            isAnalysisComplete = true
        )
    }

    private fun updateFlow() {
        val sorted = store.values.sortedByDescending { it.dateAddedEpochMs }
        _eligibleMedia.value = sorted
        _admissionProgress.value = _admissionProgress.value.copy(
            totalAdmitted = store.size
        )
    }
}
