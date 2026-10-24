package com.example.data.mediastore

import com.example.core.mediamodel.IneligibilityReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import java.util.concurrent.ConcurrentHashMap

data class IneligibleMediaEntry(
    val id: String,
    val filePath: String,
    val title: String,
    val sizeBytes: Long,
    val reasons: Set<IneligibilityReason>,
    val detectedAtEpochMs: Long
)

interface DiagnosticMediaStore {
    val ineligibleItems: StateFlow<List<IneligibleMediaEntry>>

    fun observeByReason(reason: IneligibilityReason): StateFlow<List<IneligibleMediaEntry>>
    fun recordIneligible(entry: IneligibleMediaEntry)
    fun batchRecordIneligible(entries: List<IneligibleMediaEntry>)
    fun clearDiagnostic(id: String)
}

class DefaultDiagnosticMediaStore : DiagnosticMediaStore {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val store = ConcurrentHashMap<String, IneligibleMediaEntry>()
    private val _ineligibleItems = MutableStateFlow<List<IneligibleMediaEntry>>(emptyList())
    override val ineligibleItems: StateFlow<List<IneligibleMediaEntry>> = _ineligibleItems.asStateFlow()

    private val reasonFlows = ConcurrentHashMap<IneligibilityReason, StateFlow<List<IneligibleMediaEntry>>>()

    override fun observeByReason(reason: IneligibilityReason): StateFlow<List<IneligibleMediaEntry>> {
        return reasonFlows.getOrCompute(reason) {
            ineligibleItems
                .map { list -> list.filter { it.reasons.contains(reason) } }
                .stateIn(scope, SharingStarted.Lazily, emptyList())
        }
    }

    override fun recordIneligible(entry: IneligibleMediaEntry) {
        store[entry.id] = entry
        updateFlow()
    }

    override fun batchRecordIneligible(entries: List<IneligibleMediaEntry>) {
        if (entries.isEmpty()) return
        for (entry in entries) {
            store[entry.id] = entry
        }
        updateFlow()
    }

    override fun clearDiagnostic(id: String) {
        if (store.remove(id) != null) {
            updateFlow()
        }
    }

    private fun updateFlow() {
        _ineligibleItems.value = store.values.toList()
    }
}

private inline fun <K, V> ConcurrentHashMap<K, V>.getOrCompute(key: K, compute: (K) -> V): V {
    return this[key] ?: compute(key).let { computed ->
        this.putIfAbsent(key, computed) ?: computed
    }
}
