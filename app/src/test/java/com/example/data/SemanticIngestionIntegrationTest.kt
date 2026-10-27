package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import com.example.data.semantic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SemanticIngestionIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()
    
    private val descriptor = EmbeddingModelDescriptor(
        modelId = "test", modelVersion = 1, dimensionality = 384, primaryType = SemanticRepresentationType.CONTENT
    )

    private lateinit var fakeEmbeddingProvider: FakeEmbeddingProvider
    private lateinit var retriever: SemanticCandidateRetriever

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions(
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository(dispatcher = testDispatcher)
        repository.setApplicationContextForTesting(context)
        repository.setDatabaseForTesting(database)

        val semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao())
        fakeEmbeddingProvider = FakeEmbeddingProvider(descriptor)
        
        retriever = DefaultSemanticCandidateRetriever(semanticRepo, testDispatcher)

        val indexingService = DefaultSemanticIndexingService(fakeEmbeddingProvider, retriever, semanticRepo, testDispatcher)
        val mockVisualIndexer: VisualIndexingService = mock {
            on { indexVisual(any(), any()) } doReturn EmbeddingResult.Success(SemanticRepresentation(
                id = "visual_rep", mediaId = "m1", type = SemanticRepresentationType.VISUAL,
                modelDescriptor = descriptor, dimensionality = 384, vector = FloatArray(384) { 0.1f }, sourceDataHash = "hash"
            ))
        }

        // Inject semantic components
        val fields = mapOf(
            "semanticRepresentationRepository" to semanticRepo,
            "embeddingProvider" to fakeEmbeddingProvider,
            "semanticIndexingService" to indexingService,
            "semanticCandidateRetriever" to retriever,
            "visualIndexingService" to mockVisualIndexer
        )
        
        val repoClass = MediaRepository::class.java
        fields.forEach { (name, value) ->
            val field = repoClass.getDeclaredField(name)
            field.isAccessible = true
            field.set(repository, value)
        }
    }

    @After
    fun tearDown() {
        if (::repository.isInitialized) {
            repository.close()
        }
        database.close()
    }

    private fun MediaEntity.toMediaItem(): MediaItem {
        return MediaItem(id = id, title = title, mediaType = mediaType, uriPath = uriPath)
    }

    private fun getActiveRetriever(): SemanticCandidateRetriever {
        val field = repository.javaClass.getDeclaredField("semanticCandidateRetriever")
        field.isAccessible = true
        return field.get(repository) as SemanticCandidateRetriever
    }

    @Test(timeout = 10000)
    fun `test processPendingMedia triggers embedding generation and persistence`() = runTest(testDispatcher) {
        getActiveRetriever().initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        
        val item = MediaEntity(id = "m1", title = "Sunrise", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        database.mediaDao().insert(item)
        
        repository.setMediaItemsForTesting(listOf(item.toMediaItem()))
        advanceUntilIdle()

        repository.processPendingMedia(context, 1L, setOf("external"), isManual = true)
        advanceUntilIdle()

        val representations = database.semanticRepresentationDao().getByType(SemanticRepresentationType.CONTENT.name)
        assertEquals(1, representations.size)
        assertEquals("m1", representations[0].mediaId)
        val activeRetriever = getActiveRetriever()
        assertEquals(1, activeRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    @Test(timeout = 10000)
    fun `test semantic failure does not abort media ingestion`() = runTest(testDispatcher) {
        getActiveRetriever().initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        fakeEmbeddingProvider.shouldFail = true
        
        val item = MediaEntity(id = "m1", title = "Sunrise", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        database.mediaDao().insert(item)
        
        repository.setMediaItemsForTesting(listOf(item.toMediaItem()))
        advanceUntilIdle()

        repository.processPendingMedia(context, 1L, setOf("external"), isManual = true)
        advanceUntilIdle()

        // Media should still be in DB
        val dbItem = database.mediaDao().getMediaById("m1")
        assertNotNull(dbItem)
        
        // But no semantic rep
        val representations = database.semanticRepresentationDao().getByType(SemanticRepresentationType.CONTENT.name)
        assertEquals(0, representations.size)
    }

    @Test(timeout = 10000)
    fun `test multiple items with partial semantic failure`() = runTest(testDispatcher) {
        getActiveRetriever().initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        val item1 = MediaEntity(id = "m1", title = "Success", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        val item2 = MediaEntity(id = "m2", title = "Fail", mediaType = "PHOTO", uriPath = "/path/2.jpg")
        database.mediaDao().insert(item1)
        database.mediaDao().insert(item2)
        
        repository.setMediaItemsForTesting(listOf(item1.toMediaItem(), item2.toMediaItem()))
        advanceUntilIdle()

        // Mock failure for item2 only
        fakeEmbeddingProvider.failId = "m2"

        repository.processPendingMedia(context, 1L, setOf("external"), isManual = true)
        advanceUntilIdle()

        // One success, one failure
        val representations = database.semanticRepresentationDao().getByType(SemanticRepresentationType.CONTENT.name)
        assertEquals(1, representations.size)
        assertEquals("m1", representations[0].mediaId)
    }

    private class FakeEmbeddingProvider(override val descriptor: EmbeddingModelDescriptor) : EmbeddingProvider {
        var shouldFail = false
        var failId: String? = null
        override val supportedTypes = setOf(SemanticRepresentationType.CONTENT)
        override fun isReady() = true
        override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
            if (shouldFail || mediaId == failId) {
                return EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Mock failure")
            }
            return EmbeddingResult.Success(SemanticRepresentation(
                id = "rep_$mediaId", mediaId = mediaId, type = SemanticRepresentationType.CONTENT,
                modelDescriptor = descriptor, dimensionality = 384,
                vector = FloatArray(384) { 0.1f }, sourceDataHash = sourceDataHash
            ))
        }
        override fun close() {}
    }
}
