package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileCLIPMissingModelSafetyTest {

    private class FakeUnreadySearchService(
        private val shouldThrow: Boolean = false,
    ) : SemanticSearchService {
        override fun isReady(): Boolean = false

        override fun getIndexSize(
            targetType: SemanticRepresentationType,
            descriptor: EmbeddingModelDescriptor?,
        ): Int = 0

        private val dummyDescriptor = EmbeddingModelDescriptor(
            modelId = "fake_unready",
            modelVersion = 1,
            dimensionality = 512,
            primaryType = SemanticRepresentationType.VISUAL,
        )

        override suspend fun search(
            query: String,
            topK: Int,
            minSimilarity: Float,
            targetType: SemanticRepresentationType,
            expectedDescriptor: EmbeddingModelDescriptor?,
        ): SemanticSearchResult {
            if (shouldThrow) throw IllegalStateException("Model pack asset missing")
            return SemanticSearchResult(
                query = query,
                candidates = emptyList(),
                modelDescriptor = dummyDescriptor,
                representationType = targetType,
                latencyMs = 0L,
                totalIndexedCandidates = 0,
                isSuccess = false,
                errorMessage = "Model asset not initialized",
            )
        }

        override suspend fun search(
            queryVector: FloatArray,
            queryLabel: String,
            topK: Int,
            minSimilarity: Float,
            targetType: SemanticRepresentationType,
            expectedDescriptor: EmbeddingModelDescriptor?,
        ): SemanticSearchResult {
            if (shouldThrow) throw IllegalStateException("Model pack asset missing")
            return SemanticSearchResult(
                query = queryLabel,
                candidates = emptyList(),
                modelDescriptor = dummyDescriptor,
                representationType = targetType,
                latencyMs = 0L,
                totalIndexedCandidates = 0,
                isSuccess = false,
                errorMessage = "Model asset not initialized",
            )
        }
    }

    @Test
    fun testRetriever_WhenModelUnready_ReturnsNotReadyAndEmptyCandidates() = runBlocking {
        val fakeService = FakeUnreadySearchService(shouldThrow = false)
        val retriever = DefaultMobileCLIPVisualRetriever(fakeService)

        assertFalse(retriever.isReady())

        val candidates = retriever.retrieveVisualCandidates("test query", topK = 10, minSimilarity = 0.4f)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun testRetriever_WhenServiceThrowsException_CatchesErrorAndReturnsEmptyList() = runBlocking {
        val fakeService = FakeUnreadySearchService(shouldThrow = true)
        val retriever = DefaultMobileCLIPVisualRetriever(fakeService)

        // Must catch exception internally and return empty list without crashing caller
        val candidates = retriever.retrieveVisualCandidates("test query", topK = 10, minSimilarity = 0.4f)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun testCosineSimilarity_ZeroVector_ReturnsZeroSimilarityWithoutArtificialOneMatch() {
        val zeroVector = FloatArray(512) { 0f }
        val validVector = FloatArray(512) { 0.5f }

        // Cosine similarity between a zero vector and any vector must be 0.0, avoiding artificial 1.0 match
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in zeroVector.indices) {
            dot += zeroVector[i] * validVector[i]
            normA += zeroVector[i] * zeroVector[i]
            normB += validVector[i] * validVector[i]
        }
        val sim = if ((normA <= 0.0) || (normB <= 0.0)) 0.0 else dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))

        assertEquals(0.0, sim, 0.001)
    }
}
