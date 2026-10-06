package com.example.data.semantic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileCLIPEmptyRepresentationSafetyTest {

    private val clipDescriptor = EmbeddingModelDescriptor(
        modelId = "mobileclip_s0_visual",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL,
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32
    )

    @Test
    fun testEmptyFloatArray_VectorMathAndIndex_SafelyRejected() {
        val emptyVector = FloatArray(0)
        val validVector = FloatArray(512) { 0.1f }

        // 1. safeCosineSimilarity with empty vector must return null (not 1.0)
        val simWithEmptyU = VectorMath.safeCosineSimilarity(emptyVector, validVector)
        assertNull("Empty query vector u must produce null similarity", simWithEmptyU)

        val simWithEmptyV = VectorMath.safeCosineSimilarity(validVector, emptyVector)
        assertNull("Empty candidate vector v must produce null similarity", simWithEmptyV)

        // 2. Direct validateVector call must throw IllegalArgumentException for FloatArray(0)
        var exceptionThrown = false
        try {
            VectorMath.validateVector(emptyVector)
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("validateVector must throw for FloatArray(0)", exceptionThrown)

        // 3. Attempting to create SemanticRepresentation with FloatArray(0) must fail
        var repException = false
        try {
            SemanticRepresentation(
                id = "rep_invalid",
                mediaId = "media_invalid",
                type = SemanticRepresentationType.VISUAL,
                modelDescriptor = EmbeddingModelDescriptor(
                    modelId = "test_model",
                    modelVersion = 1,
                    dimensionality = 0,
                    primaryType = SemanticRepresentationType.VISUAL
                ),
                dimensionality = 0,
                vector = emptyVector,
                sourceDataHash = "hash"
            )
        } catch (_: IllegalArgumentException) {
            repException = true
        }
        assertTrue("SemanticRepresentation init must reject FloatArray(0)", repException)
    }

    @Test
    fun testEmptyQueryVector_InMemoryVectorIndex_ReturnsNoCandidates() {
        val index = InMemoryVectorIndex(clipDescriptor, SemanticRepresentationType.VISUAL)

        val validVector = FloatArray(512) { 0.1f }
        val normalizedValid = VectorMath.l2Normalize(validVector)
        val representation = SemanticRepresentation(
            id = "rep_1",
            mediaId = "media_1",
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = clipDescriptor,
            dimensionality = 512,
            vector = normalizedValid,
            sourceDataHash = "hash1"
        )
        index.add(representation)
        assertEquals(1, index.size)

        // Query with empty vector must return emptyList (0 candidates) and not match candidate with score 1.0
        val candidates = index.query(queryVector = FloatArray(0), topK = 10, minSimilarity = 0.0f)
        assertTrue("Empty query vector must return zero candidates", candidates.isEmpty())
    }

    @Test
    fun testMismatchedDimensionality_VectorMathAndIndex_SafelyFailsWithoutArtificialMatch() {
        val vector128 = FloatArray(128) { 0.1f }
        val vector512 = FloatArray(512) { 0.1f }

        // 1. safeCosineSimilarity between mismatched dimensions must return null
        val sim = VectorMath.safeCosineSimilarity(vector128, vector512)
        assertNull("Mismatched dimensions (128 vs 512) must produce null similarity", sim)

        // 2. Querying 512d index with 128d vector must be safely handled
        val index = InMemoryVectorIndex(clipDescriptor, SemanticRepresentationType.VISUAL)
        val representation = SemanticRepresentation(
            id = "rep_512",
            mediaId = "media_512",
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = clipDescriptor,
            dimensionality = 512,
            vector = VectorMath.l2Normalize(vector512),
            sourceDataHash = "hash512"
        )
        index.add(representation)

        var queryThrewException = false
        try {
            index.query(queryVector = vector128, topK = 10, minSimilarity = 0.0f)
        } catch (_: IllegalArgumentException) {
            queryThrewException = true
        }
        assertTrue("Query with mismatched dimension must fail gracefully via exception or empty list", queryThrewException)
    }

    @Test
    fun testValidEmbedding_ProducesCorrectCosineSimilarityAndRetrieval() = runTest {
        val index = InMemoryVectorIndex(clipDescriptor, SemanticRepresentationType.VISUAL)

        val rawVector = FloatArray(512) { (it + 1).toFloat() }
        val normalized = VectorMath.l2Normalize(rawVector)

        val rep = SemanticRepresentation(
            id = "rep_valid",
            mediaId = "media_valid",
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = clipDescriptor,
            dimensionality = 512,
            vector = normalized,
            sourceDataHash = "hash_valid"
        )
        index.add(rep)

        // 1. Cosine similarity of normalized vector with itself is 1.0f
        val sim = VectorMath.cosineSimilarity(normalized, normalized)
        assertEquals(1.0f, sim, 0.0001f)

        // 2. Querying index with identical valid vector retrieves candidate with score ~1.0f
        val results = index.query(queryVector = normalized, topK = 5, minSimilarity = 0.5f)
        assertEquals(1, results.size)
        assertEquals("media_valid", results[0].mediaId)
        assertEquals(1.0f, results[0].similarityScore, 0.001f)
    }
}
