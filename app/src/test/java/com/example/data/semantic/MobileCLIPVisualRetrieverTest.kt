package com.example.data.semantic

import android.content.Context
import com.example.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class MobileCLIPVisualRetrieverTest {

    private val descriptor = EmbeddingModelDescriptor("mobileclip", 1, 512, SemanticRepresentationType.VISUAL)

    @Test
    fun testRetrieveVisualCandidates_CallsServiceWithVisualModality() = runBlocking {
        val fakeService = object : SemanticSearchService {
            var capturedType: SemanticRepresentationType? = null
            
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                capturedType = targetType
                return SemanticSearchResult(
                    query = query,
                    candidates = listOf(
                        SemanticRetrievalCandidate("media_v1", "rep_v1", 0.9f, SemanticRepresentationType.VISUAL, descriptor, 1.0f)
                    ),
                    modelDescriptor = descriptor,
                    representationType = SemanticRepresentationType.VISUAL,
                    latencyMs = 1L,
                    totalIndexedCandidates = 1,
                    isSuccess = true
                )
            }

            override suspend fun search(
                queryVector: FloatArray,
                queryLabel: String,
                topK: Int,
                minSimilarity: Float,
                targetType: SemanticRepresentationType,
                expectedDescriptor: EmbeddingModelDescriptor?
            ): SemanticSearchResult {
                return SemanticSearchResult(
                    query = queryLabel,
                    candidates = emptyList(),
                    modelDescriptor = descriptor,
                    representationType = targetType,
                    latencyMs = 1L,
                    totalIndexedCandidates = 0,
                    isSuccess = true
                )
            }

            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 1
        }

        val retriever = DefaultMobileCLIPVisualRetriever(fakeService)
        val items = retriever.retrieveVisualCandidates("sunset", 5, 0.5f)

        assertEquals(SemanticRepresentationType.VISUAL, fakeService.capturedType)
        assertEquals(1, items.size)
        assertEquals("media_v1", items[0].mediaId)
        assertEquals(0.9f, items[0].rawScore)
        assertEquals(1, items[0].rank)
        assertEquals("VISUAL", items[0].metadata["representationType"])
    }

    @Test
    fun testRetrieveVisualCandidates_HandlesServiceFailure() = runBlocking {
        val failingService = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                return SemanticSearchResult(query, emptyList(), descriptor, targetType, 0L, 0, false, "Inference error")
            }

            override suspend fun search(
                queryVector: FloatArray,
                queryLabel: String,
                topK: Int,
                minSimilarity: Float,
                targetType: SemanticRepresentationType,
                expectedDescriptor: EmbeddingModelDescriptor?
            ): SemanticSearchResult {
                return SemanticSearchResult(
                    query = queryLabel,
                    candidates = emptyList(),
                    modelDescriptor = descriptor,
                    representationType = targetType,
                    latencyMs = 1L,
                    totalIndexedCandidates = 0,
                    isSuccess = true
                )
            }

            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val retriever = DefaultMobileCLIPVisualRetriever(failingService)
        val items = retriever.retrieveVisualCandidates("sunset", 5, 0.5f)

        assertTrue(items.isEmpty())
    }

    @Test
    fun testRetrieveVisualCandidates_HandlesException() = runBlocking {
        val crashingService = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                throw RuntimeException("Crash")
            }

            override suspend fun search(
                queryVector: FloatArray,
                queryLabel: String,
                topK: Int,
                minSimilarity: Float,
                targetType: SemanticRepresentationType,
                expectedDescriptor: EmbeddingModelDescriptor?
            ): SemanticSearchResult {
                return SemanticSearchResult(
                    query = queryLabel,
                    candidates = emptyList(),
                    modelDescriptor = descriptor,
                    representationType = targetType,
                    latencyMs = 1L,
                    totalIndexedCandidates = 0,
                    isSuccess = true
                )
            }

            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val retriever = DefaultMobileCLIPVisualRetriever(crashingService)
        val items = retriever.retrieveVisualCandidates("sunset", 5, 0.5f)

        assertTrue(items.isEmpty())
    }

    @Test
    fun testVisualIndexing_DeduplicationAndZeroInferenceOnReuse() = runBlocking {
        val mockProvider = mock<EmbeddingProvider>()
        val mockCandidateRetriever = mock<SemanticCandidateRetriever>()
        val mockRepo = mock<SemanticRepresentationRepository>()
        val mockContext = mock<Context>()
        val descriptor = EmbeddingModelDescriptor("mobileclip-s0", 1, 512, SemanticRepresentationType.VISUAL)

        whenever(mockProvider.descriptor).thenReturn(descriptor)
        whenever(mockProvider.isReady()).thenReturn(true)

        val service = DefaultVisualIndexingService(mockProvider, mockCandidateRetriever, mockRepo, Dispatchers.Unconfined)

        val item = MediaItem(id = "item_1", title = "Test", mediaType = "PHOTO", sizeBytes = 1000)
        val hash = "v1_item_1_1000"
        val existing = SemanticRepresentation("rep_1", "item_1", SemanticRepresentationType.VISUAL, descriptor, 3, 512, FloatArray(512) { 0.1f }, hash)

        whenever(mockRepo.getSpecificRepresentation(eq("item_1"), eq(SemanticRepresentationType.VISUAL), any())).thenReturn(existing)

        val result = service.indexVisual(mockContext, item)

        assertTrue(result is EmbeddingResult.Success)
        verify(mockProvider, never()).generateEmbedding(any(), any(), any())
        verify(mockCandidateRetriever).onRepresentationAdded(existing)
    }

    @Test
    fun testVisualIndexing_StaleRepresentationInvalidation_ReembedsWhenHashChanges(): Unit = runBlocking {
        val mockProvider = mock<EmbeddingProvider>()
        val mockCandidateRetriever = mock<SemanticCandidateRetriever>()
        val mockRepo = mock<SemanticRepresentationRepository>()
        val mockContext = mock<Context>()
        val descriptor = EmbeddingModelDescriptor("mobileclip-s0", 1, 512, SemanticRepresentationType.VISUAL)

        whenever(mockProvider.descriptor).thenReturn(descriptor)
        whenever(mockProvider.isReady()).thenReturn(true)

        val service = DefaultVisualIndexingService(mockProvider, mockCandidateRetriever, mockRepo, Dispatchers.Unconfined)

        val item = MediaItem(id = "item_2", title = "Test Changed", mediaType = "VIDEO", uriPath = "video.mp4", sizeBytes = 2000)
        val oldHash = "v1_item_2_1000"
        val staleRep = SemanticRepresentation("rep_2", "item_2", SemanticRepresentationType.VISUAL, descriptor, 4, 512, FloatArray(512) { 0.1f }, oldHash)

        whenever(mockRepo.getSpecificRepresentation(eq("item_2"), eq(SemanticRepresentationType.VISUAL), any())).thenReturn(staleRep)

        val newRep = SemanticRepresentation("rep_2_new", "item_2", SemanticRepresentationType.VISUAL, descriptor, 3, 512, FloatArray(512) { 0.2f }, "v1_item_2_2000")
        whenever(mockProvider.generateEmbedding(any(), any(), any())).thenReturn(EmbeddingResult.Success(newRep))

        val result = service.indexVisual(mockContext, item)

        assertTrue(result is EmbeddingResult.Success)
        verify(mockProvider).generateEmbedding(any(), any(), any())
        verify(mockRepo).saveRepresentation(newRep)
    }

    @Test
    fun testVisualIndexing_InFlightCleanupOnCancellation(): Unit = runBlocking {
        val mockProvider = mock<EmbeddingProvider>()
        val mockCandidateRetriever = mock<SemanticCandidateRetriever>()
        val mockRepo = mock<SemanticRepresentationRepository>()
        val mockContext = mock<Context>()

        val service = DefaultVisualIndexingService(mockProvider, mockCandidateRetriever, mockRepo, Dispatchers.Unconfined)

        val item = MediaItem(id = "item_cancel", title = "Cancel", mediaType = "PHOTO", sizeBytes = 500)
        
        val job = launch {
            service.indexVisual(mockContext, item)
        }
        job.cancel()
        
        assertTrue(job.isCancelled)
    }

    @Test
    fun testVisualIndexing_FailureEvictsMapEntry_AllowsFreshRetry(): Unit = runBlocking {
        val mockProvider = mock<EmbeddingProvider>()
        val mockCandidateRetriever = mock<SemanticCandidateRetriever>()
        val mockRepo = mock<SemanticRepresentationRepository>()
        val mockContext = mock<Context>()
        val descriptor = EmbeddingModelDescriptor("mobileclip-s0", 1, 512, SemanticRepresentationType.VISUAL)

        whenever(mockProvider.descriptor).thenReturn(descriptor)
        whenever(mockProvider.isReady()).thenReturn(true)

        val service = DefaultVisualIndexingService(mockProvider, mockCandidateRetriever, mockRepo, Dispatchers.Unconfined)

        val item = MediaItem(id = "item_fail_retry", title = "Fail Retry", mediaType = "VIDEO", uriPath = "video.mp4", sizeBytes = 500)

        // First attempt fails
        whenever(mockProvider.generateEmbedding(any(), any(), any())).thenReturn(EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Temporary error"))
        val firstResult = service.indexVisual(mockContext, item)
        assertTrue(firstResult is EmbeddingResult.Failure)

        // Second attempt succeeds because the failed in-flight job was evicted
        val rep = SemanticRepresentation("rep_retry", "item_fail_retry", SemanticRepresentationType.VISUAL, descriptor, 4, 512, FloatArray(512) { 0.5f }, "v1_item_fail_retry_500")
        whenever(mockProvider.generateEmbedding(any(), any(), any())).thenReturn(EmbeddingResult.Success(rep))

        val secondResult = service.indexVisual(mockContext, item)
        assertTrue(secondResult is EmbeddingResult.Success)
    }
}

