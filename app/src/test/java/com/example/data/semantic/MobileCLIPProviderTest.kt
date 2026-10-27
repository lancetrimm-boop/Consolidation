package com.example.data.semantic

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MobileCLIPProviderTest {

    private lateinit var provider: MobileCLIPEmbeddingProvider
    private val engine: MobileCLIPInferenceEngine = mock()

    @Before
    fun setUp() {
        provider = MobileCLIPEmbeddingProvider(engine)
        whenever(engine.isLoaded()).thenReturn(true)
    }

    @Test
    fun testGenerateEmbedding_Success() = runBlocking {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        
        // Mock engine output: 512-dimensional vector
        val rawVector = FloatArray(512) { 1.0f } 
        whenever(engine.infer(any(), any(), any())).thenReturn(rawVector)

        val result = provider.generateEmbedding("m1", SemanticInput.ExplicitBitmap(bitmap), "hash1")

        assertTrue("Expected EmbeddingResult.Success, got $result", result is EmbeddingResult.Success)
        val rep = (result as EmbeddingResult.Success).representation
        assertEquals(512, rep.dimensionality)
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
        assertEquals(SemanticRepresentationType.VISUAL, rep.type)
    }

    @Test
    fun testTextProvider_GeneratesCompatibleVector() = runBlocking {
        val textEngine: MobileCLIPTextInferenceEngine = mock()
        val tokenizer: ClipBpeTokenizer = mock()
        
        whenever(textEngine.isLoaded()).thenReturn(true)
        whenever(tokenizer.tokenize(any())).thenReturn(LongArray(77) { 0L })
        whenever(textEngine.infer(any())).thenReturn(FloatArray(512) { 0.5f })

        val textProvider = MobileCLIPTextEmbeddingProvider(textEngine, tokenizer)
        val result = textProvider.generateEmbedding("query_1", SemanticInput.Text("sunset"), "h1")

        assertTrue("Expected EmbeddingResult.Success, got $result", result is EmbeddingResult.Success)
        val rep = (result as EmbeddingResult.Success).representation
        assertEquals(512, rep.dimensionality)
        // Magnitude should be 1.0 due to L2 normalization in provider
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
    }
}
