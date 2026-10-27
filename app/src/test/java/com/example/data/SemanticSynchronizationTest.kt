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
class SemanticSynchronizationTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()
    
    private val descriptor = EmbeddingModelDescriptor(
        modelId = "test", modelVersion = 1, dimensionality = 384, primaryType = SemanticRepresentationType.CONTENT
    )

    private lateinit var semanticRepo: SemanticRepresentationRepository
    private lateinit var retriever: SemanticCandidateRetriever
    private lateinit var fakeEmbeddingProvider: FakeEmbeddingProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository(dispatcher = testDispatcher)
        repository.setApplicationContextForTesting(context)
        repository.setDatabaseForTesting(database)

        semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao(), testDispatcher)
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
    fun `test addition synchronization`() = runTest(testDispatcher) {
        getActiveRetriever().initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        val item = MediaEntity(id = "m1", title = "New Image", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        database.mediaDao().insert(item)
        
        repository.setMediaItemsForTesting(listOf(item.toMediaItem()))
        advanceUntilIdle()
        
        // Trigger manual processing which should trigger semantic indexing
        repository.processPendingMedia(context, 1L, setOf("external"), isManual = true)
        advanceUntilIdle()
        
        // Verify index has the item
        val activeRetriever = getActiveRetriever()
        assertEquals("Index should have 1 item after ingestion", 1, activeRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    @Test(timeout = 10000)
    fun `test deletion synchronization`() = runTest(testDispatcher) {
        getActiveRetriever().initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        // Setup initial item
        val item = MediaEntity(id = "m1", title = "To Delete", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        database.mediaDao().insert(item)
        repository.setMediaItemsForTesting(listOf(item.toMediaItem()))
        repository.processPendingMedia(context, 1L, setOf("external"), isManual = true)
        advanceUntilIdle()
        
        val activeRetriever = getActiveRetriever()
        assertEquals(1, activeRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))

        // Delete via repository
        repository.deleteMediaItemSuspend("m1")
        advanceUntilIdle()
        
        // Verify index is empty
        assertEquals("Index should be empty after deletion", 0, activeRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    private class FakeEmbeddingProvider(override val descriptor: EmbeddingModelDescriptor) : EmbeddingProvider {
        override val supportedTypes = setOf(SemanticRepresentationType.CONTENT)
        override fun isReady() = true
        override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
            return EmbeddingResult.Success(SemanticRepresentation(
                id = "rep_$mediaId", mediaId = mediaId, type = SemanticRepresentationType.CONTENT,
                modelDescriptor = descriptor, dimensionality = 384,
                vector = FloatArray(384) { 0.1f }, sourceDataHash = sourceDataHash
            ))
        }
        override fun close() {}
    }

    @Test(timeout = 10000)
    fun `test deletion after active media check`() = runTest(testDispatcher) {
        val activeRetriever = getActiveRetriever()
        activeRetriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)

        val item = MediaEntity(id = "m1", title = "Race Item", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        database.mediaDao().insert(item)

        val pauseLatch = CompletableDeferred<Unit>()
        val resumeLatch = CompletableDeferred<Unit>()

        val suspendingProvider = object : EmbeddingProvider {
            override val descriptor = this@SemanticSynchronizationTest.descriptor
            override val supportedTypes = setOf(SemanticRepresentationType.CONTENT)
            override fun isReady() = true
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                pauseLatch.complete(Unit)
                resumeLatch.await()
                return EmbeddingResult.Success(SemanticRepresentation(
                    id = "rep_$mediaId", mediaId = mediaId, type = SemanticRepresentationType.CONTENT,
                    modelDescriptor = descriptor, dimensionality = 384,
                    vector = FloatArray(384) { 0.1f }, sourceDataHash = sourceDataHash
                ))
            }
            override fun close() {}
        }

        val indexingService = DefaultSemanticIndexingService(suspendingProvider, activeRetriever, semanticRepo, testDispatcher)

        val indexingJob = backgroundScope.launch(testDispatcher) {
            indexingService.indexMediaItem(item.toMediaItem())
        }

        pauseLatch.await()

        repository.deleteMediaItemSuspend("m1")

        resumeLatch.complete(Unit)
        indexingJob.join()
        advanceUntilIdle()

        val reps = database.semanticRepresentationDao().getByType(SemanticRepresentationType.CONTENT.name)
        assertTrue("Deleted item representation should not be persisted after concurrent deletion", reps.isEmpty())
        assertEquals("Candidate index must exclude deleted item", 0, activeRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    @Test(timeout = 10000)
    fun `test concurrent index initialization and deletion`() = runTest(testDispatcher) {
        val item = MediaEntity(id = "m1", title = "Hydration Item", mediaType = "PHOTO", uriPath = "/path/1.jpg")
        database.mediaDao().insert(item)
        val rep = SemanticRepresentation(
            id = "rep_m1", mediaId = "m1", type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor, dimensionality = 384,
            vector = FloatArray(384) { 0.1f }, sourceDataHash = "hash"
        )
        semanticRepo.saveRepresentation(rep)

        val pauseLatch = CompletableDeferred<Unit>()
        val resumeLatch = CompletableDeferred<Unit>()

        val pausingRepo = object : SemanticRepresentationRepository by semanticRepo {
            override suspend fun getCompatibleRepresentations(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): List<SemanticRepresentation> {
                val list = semanticRepo.getCompatibleRepresentations(type, descriptor)
                pauseLatch.complete(Unit)
                resumeLatch.await()
                return list
            }
        }

        val testRetriever = DefaultSemanticCandidateRetriever(pausingRepo, testDispatcher)

        val initJob = backgroundScope.launch(testDispatcher) {
            testRetriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        }

        pauseLatch.await()

        database.mediaDao().deleteById("m1")
        database.mediaDao().update(item.copy(isDeleted = true))

        resumeLatch.complete(Unit)
        initJob.join()
        advanceUntilIdle()

        assertEquals("Candidate index must exclude deleted item even if read during hydration", 0, testRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    @Test(timeout = 10000)
    fun `test initialization failure and retry`() = runTest(testDispatcher) {
        var callCount = 0
        var failFirst = true
        val flakyRepo = object : SemanticRepresentationRepository by semanticRepo {
            override suspend fun getCompatibleRepresentations(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): List<SemanticRepresentation> {
                callCount++
                if (failFirst) {
                    throw RuntimeException("Simulated initialization failure")
                }
                return semanticRepo.getCompatibleRepresentations(type, descriptor)
            }
        }

        val testRetriever = DefaultSemanticCandidateRetriever(flakyRepo, testDispatcher)

        var exceptionCaught = false
        try {
            testRetriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        } catch (_: Exception) {
            exceptionCaught = true
        }
        assertTrue("First initialization attempt must throw exception", exceptionCaught)
        assertEquals(1, callCount)

        val item = MediaEntity(id = "m2", title = "Retry Item", mediaType = "PHOTO", uriPath = "/path/2.jpg")
        database.mediaDao().insert(item)
        val rep = SemanticRepresentation(
            id = "rep_m2", mediaId = "m2", type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor, dimensionality = 384,
            vector = FloatArray(384) { 0.1f }, sourceDataHash = "hash"
        )
        semanticRepo.saveRepresentation(rep)

        failFirst = false
        testRetriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        advanceUntilIdle()

        assertEquals("Retry attempt must execute successfully", 2, callCount)
        assertEquals("Index must hydrate successfully after retry", 1, testRetriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }
}
