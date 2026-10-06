package com.example.data.intelligence

import com.example.ui.models.TraceEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DecisionTraceCollectorTest {

    @Before
    fun setUp() {
        // Clear test traces
        for (i in 0..150) {
            DecisionTraceCollector.clearTrace("test_req_$i")
        }
    }

    @Test
    fun testStartTraceAndLogEvent_PreservesSequenceOrdering() {
        val reqId = "test_req_seq"
        DecisionTraceCollector.startTrace(reqId, "SEARCH")
        DecisionTraceCollector.logEvent(reqId, TraceEventType.CHANNEL_RETRIEVAL_START, "Retrieval started")
        DecisionTraceCollector.logEvent(reqId, TraceEventType.CHANNEL_RETRIEVAL_COMPLETE, "Retrieval complete")
        DecisionTraceCollector.logEvent(reqId, TraceEventType.RANK_ASSIGNED, "Rank assigned")

        val trace = DecisionTraceCollector.getTrace(reqId)
        assertNotNull(trace)
        assertEquals(reqId, trace?.requestId)
        assertEquals("SEARCH", trace?.surface)
        assertEquals(4, trace?.events?.size)

        val events = trace!!.events
        assertEquals(0L, events[0].sequenceNumber)
        assertEquals(TraceEventType.REQUEST_RECEIVED, events[0].type)

        assertEquals(1L, events[1].sequenceNumber)
        assertEquals(TraceEventType.CHANNEL_RETRIEVAL_START, events[1].type)

        assertEquals(2L, events[2].sequenceNumber)
        assertEquals(TraceEventType.CHANNEL_RETRIEVAL_COMPLETE, events[2].type)

        assertEquals(3L, events[3].sequenceNumber)
        assertEquals(TraceEventType.RANK_ASSIGNED, events[3].type)

        DecisionTraceCollector.clearTrace(reqId)
    }

    @Test
    fun testConcurrentEventsOnSameRequest_ThreadSafeAndNoLoss() = runBlocking(Dispatchers.Default) {
        val reqId = "test_req_concurrent"
        DecisionTraceCollector.startTrace(reqId, "SORT")

        val jobs = List(10) { threadIdx ->
            launch {
                repeat(20) { itemIdx ->
                    DecisionTraceCollector.logEvent(reqId, TraceEventType.SCORING_COMPLETED, "Thread $threadIdx item $itemIdx")
                }
            }
        }
        jobs.joinAll()

        val trace = DecisionTraceCollector.getTrace(reqId)
        assertNotNull(trace)
        // 1 startTrace + (10 threads * 20 events) = 201 events
        assertEquals(201, trace?.events?.size)

        // Verify sequence numbers are unique and ascending from 0 to 200
        val sequenceNumbers = trace!!.events.map { it.sequenceNumber }.sorted()
        assertEquals((0L..200L).toList(), sequenceNumbers)

        DecisionTraceCollector.clearTrace(reqId)
    }

    @Test
    fun testGenuinelyOverlappingRequests_RemainIsolated() = runBlocking(Dispatchers.Default) {
        val reqA = "test_req_A"
        val reqB = "test_req_B"

        val jobA = launch {
            DecisionTraceCollector.startTrace(reqA, "SEARCH_A")
            repeat(50) { i ->
                DecisionTraceCollector.logEvent(reqA, TraceEventType.CHANNEL_RETRIEVAL_START, "A_$i")
            }
        }

        val jobB = launch {
            DecisionTraceCollector.startTrace(reqB, "SEARCH_B")
            repeat(50) { i ->
                DecisionTraceCollector.logEvent(reqB, TraceEventType.CHANNEL_RETRIEVAL_START, "B_$i")
            }
        }

        jobA.join()
        jobB.join()

        val traceA = DecisionTraceCollector.getTrace(reqA)
        val traceB = DecisionTraceCollector.getTrace(reqB)

        assertNotNull(traceA)
        assertNotNull(traceB)

        assertEquals(51, traceA?.events?.size)
        assertEquals(51, traceB?.events?.size)

        assertTrue(traceA!!.events.all { !it.detail.startsWith("B_") })
        assertTrue(traceB!!.events.all { !it.detail.startsWith("A_") })

        DecisionTraceCollector.clearTrace(reqA)
        DecisionTraceCollector.clearTrace(reqB)
    }

    @Test
    fun testBoundedTraceRetention_TrimsOldestTraces() {
        for (i in 1..120) {
            val id = "test_req_lru_$i"
            DecisionTraceCollector.startTrace(id, "SURFACE_$i")
            DecisionTraceCollector.logEvent(id, TraceEventType.RANK_ASSIGNED, "Done $i")
        }

        // Oldest trace test_req_lru_1 should be evicted since MAX_TRACES is 100
        assertNull(DecisionTraceCollector.getTrace("test_req_lru_1"))
        assertNull(DecisionTraceCollector.getTrace("test_req_lru_10"))
        assertNotNull(DecisionTraceCollector.getTrace("test_req_lru_120"))

        for (i in 1..120) {
            DecisionTraceCollector.clearTrace("test_req_lru_$i")
        }
    }
}
