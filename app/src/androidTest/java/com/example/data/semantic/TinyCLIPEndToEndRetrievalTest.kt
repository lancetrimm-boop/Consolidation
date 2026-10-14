package com.example.data.semantic

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.db.AuraDatabase
import com.example.util.ModelAssetLoader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TinyCLIPEndToEndRetrievalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AuraDatabase
    private lateinit var semanticRepo: RoomSemanticRepresentationRepository
    private lateinit var visionEngine: OnnxRuntimeMobileCLIPInferenceEngine
    private lateinit var textEngine: OnnxRuntimeMobileCLIPTextInferenceEngine
    private lateinit var visionProvider: TinyCLIPEmbeddingProvider
    private lateinit var textProvider: TinyCLIPTextEmbeddingProvider
    private lateinit var retriever: DefaultSemanticCandidateRetriever
    private lateinit var searchService: DefaultSemanticSearchService

    @Before
    fun setUp() {
        // 1. Isolated In-Memory Database (zero production impact)
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao())

        // 2. Load Standalone TinyCLIP ONNX Assets
        val visionPath = ModelAssetLoader.getLocalPath(context, "models/tinyclip_vision.onnx")
        val textPath = ModelAssetLoader.getLocalPath(context, "models/tinyclip_text.onnx")

        visionEngine = OnnxRuntimeMobileCLIPInferenceEngine(modelPath = visionPath)
        textEngine = OnnxRuntimeMobileCLIPTextInferenceEngine(modelPath = textPath)

        visionProvider = TinyCLIPEmbeddingProvider(visionEngine)

        val vocabJson = context.assets.open("models/mobileclip_vocab.json").use { it.bufferedReader().readText() }
        val mergesTxt = context.assets.open("models/mobileclip_merges.txt").use { it.bufferedReader().readText() }
        val tokenizer = ClipBpeTokenizer.fromAssets(vocabJson, mergesTxt)
        textProvider = TinyCLIPTextEmbeddingProvider(textEngine, tokenizer)

        retriever = DefaultSemanticCandidateRetriever(semanticRepo)
        searchService = DefaultSemanticSearchService(visionProvider, retriever)
    }

    @After
    fun tearDown() {
        visionProvider.close()
        textProvider.close()
        database.close()
    }

    @Test
    fun testTinyCLIPVisionEmbeddingGenerationAndPersistence() = runTest {
        // Create synthetic bitmap fixture
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        for (x in 0 until 224) {
            for (y in 0 until 224) {
                bitmap.setPixel(x, y, Color.rgb(120, 150, 200))
            }
        }

        // Generate embedding via provider
        val mediaId = "test_media_1"
        val hash = "hash_1"
        val result = visionProvider.generateEmbedding(mediaId, SemanticInput.ExplicitBitmap(bitmap), hash)
        assertTrue("Embedding generation must succeed", result is EmbeddingResult.Success)

        val representation = (result as EmbeddingResult.Success).representation
        assertEquals(512, representation.dimensionality)
        assertEquals("tinyclip-vit-8m-16", representation.modelDescriptor.modelId)
        assertEquals(1.0f, VectorMath.magnitude(representation.vector), 1e-4f)

        // Persist in isolated database
        semanticRepo.saveRepresentation(representation)
        retriever.initializeIndex(representation.type, representation.modelDescriptor)

        // Verify retrieval via search service
        val searchResult = searchService.search(
            queryVector = representation.vector,
            queryLabel = "Test Vector",
            topK = 5,
            targetType = SemanticRepresentationType.VISUAL,
            expectedDescriptor = representation.modelDescriptor
        )

        assertTrue("Search must succeed", searchResult.isSuccess)
        assertTrue("Must find the persisted candidate", searchResult.hasMatches)
        assertEquals(mediaId, searchResult.topMatch?.mediaId)
    }

    @Test
    fun testIncompatibleDescriptorRejection() = runTest {
        // Query with an incompatible expected descriptor constraint
        val incompatibleDesc = EmbeddingModelDescriptor(
            modelId = "mobileclip-s0",
            modelVersion = 1,
            dimensionality = 512,
            primaryType = SemanticRepresentationType.VISUAL
        )

        val searchResult = searchService.search(
            query = "sunset over water",
            topK = 5,
            targetType = SemanticRepresentationType.VISUAL,
            expectedDescriptor = incompatibleDesc
        )

        assertFalse("Search with incompatible expected descriptor must fail", searchResult.isSuccess)
        assertTrue("Candidates must be empty on descriptor rejection", searchResult.candidates.isEmpty())
        assertNotNull("Error message must be present", searchResult.errorMessage)
    }

    @Test
    fun testMissingRepresentationSafety() = runTest {
        val searchResult = searchService.search(
            queryVector = FloatArray(512) { 0.1f },
            queryLabel = "Missing Rep Test",
            topK = 5,
            targetType = SemanticRepresentationType.VISUAL,
            expectedDescriptor = visionProvider.descriptor
        )

        assertTrue(searchResult.isSuccess)
        assertTrue(searchResult.candidates.isEmpty())
    }
}
