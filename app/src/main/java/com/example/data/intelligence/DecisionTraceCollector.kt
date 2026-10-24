package com.example.data.intelligence

import android.util.Log
import com.example.ui.models.DecisionTrace
import com.example.ui.models.TraceEvent
import com.example.ui.models.TraceEventType
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, bounded in-memory collector for production decision traces.
 * Developer-only observational tool.
 */
object DecisionTraceCollector {

    private const val MAX_TRACES = 100

    private val traces = ConcurrentHashMap<String, MutableList<TraceEvent>>()
    private val traceMetadata = ConcurrentHashMap<String, String>() // requestId -> surface
    private val sequenceCounters = ConcurrentHashMap<String, AtomicLong>()
    private val traceOrder = ConcurrentLinkedQueue<String>()

    fun startTrace(requestId: String, surface: String) {
        if (!com.example.BuildConfig.ENABLE_DEVELOPER_TOOLS) return
        val seqCounter = sequenceCounters.computeIfAbsent(requestId) { AtomicLong(0L) }
        val seq = seqCounter.getAndIncrement()
        val event = TraceEvent(sequenceNumber = seq, type = TraceEventType.REQUEST_RECEIVED, detail = "Surface: $surface")
        
        val eventList = Collections.synchronizedList(mutableListOf(event))
        traces[requestId] = eventList
        traceMetadata[requestId] = surface
        traceOrder.add(requestId)

        trimOldTraces()
        Log.i("AURA_INTEL_TRACE", "[$requestId #$seq] ${event.type.name}: ${event.detail}")
    }

    fun logEvent(requestId: String, type: TraceEventType, detail: String = "", metadata: Map<String, String> = emptyMap()) {
        if (!com.example.BuildConfig.ENABLE_DEVELOPER_TOOLS) return
        val seqCounter = sequenceCounters[requestId] ?: return
        val seq = seqCounter.getAndIncrement()
        val event = TraceEvent(sequenceNumber = seq, type = type, detail = detail, metadata = metadata)
        
        traces[requestId]?.add(event)
        
        val metaStr = if (metadata.isNotEmpty()) " | metadata=$metadata" else ""
        Log.i("AURA_INTEL_TRACE", "[$requestId #$seq] ${event.type.name}: ${event.detail}$metaStr")
    }

    fun getTrace(requestId: String): DecisionTrace? {
        val eventList = traces[requestId] ?: return null
        val events = synchronized(eventList) { eventList.toList() }
        val surface = traceMetadata[requestId] ?: "UNKNOWN"
        return DecisionTrace(requestId, surface, events)
    }

    fun clearTrace(requestId: String) {
        traces.remove(requestId)
        traceMetadata.remove(requestId)
        sequenceCounters.remove(requestId)
        traceOrder.remove(requestId)
    }

    private fun trimOldTraces() {
        while (traceOrder.size > MAX_TRACES) {
            val oldest = traceOrder.poll() ?: break
            clearTrace(oldest)
        }
    }
}

