package com.example.data.semantic

import com.example.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Service responsible for the end-to-end semantic indexing pipeline.
 *
 * Coordinates:
 * 1. MediaItem -> Semantic Document synthesis
 * 2. Text -> Neural Embedding generation
 * 3. Persistence to Repository
 * 4. Update to in-memory Vector Index
 */
interface SemanticIndexingService {
    /**
     * Generates and persists a semantic representation for the given MediaItem.
     * Updates the in-memory index upon success.
     * 
     * Optimizes by reusing existing valid embeddings if metadata and model have not changed.
     */
    suspend fun indexMediaItem(item: MediaItem): EmbeddingResult

    /**
     * Efficiently indexes a batch of media items.
     */
    suspend fun indexBatch(items: List<MediaItem>)

    /**
     * Executes the given block under the per-media-ID coroutine mutex lock.
     */
    suspend fun <T> withMediaLock(mediaId: String, block: suspend () -> T): T
}

/**
 * Production implementation of [SemanticIndexingService].
 */
class DefaultSemanticIndexingService(
    internal val embeddingProvider: EmbeddingProvider,
    private val candidateRetriever: SemanticCandidateRetriever,
    private val repository: SemanticRepresentationRepository,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    private val isMediaActive: suspend (String) -> Boolean = { true }
) : SemanticIndexingService {

    private val mediaLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun indexMediaItem(item: MediaItem): EmbeddingResult = withContext(dispatcher) {
        try {
            // 1. Synthesize structured document from available metadata
            val document = SemanticDocumentBuilder.buildDocument(item)
            val currentDocVersion = SemanticDocumentBuilder.DOCUMENT_VERSION
            
            // 2. Generate content hash for change detection
            val sourceHash = computeSha256(document)

            // 3. Optimization: Check for existing valid representation
            val descriptor = embeddingProvider.descriptor
            val existing = repository.getSpecificRepresentation(
                mediaId = item.id,
                type = descriptor.primaryType,
                descriptor = descriptor
            )

            val mutex = mediaLocks.getOrPut(item.id) { Mutex() }

            if (existing != null && 
                existing.documentVersion == currentDocVersion && 
                existing.sourceDataHash == sourceHash) {
                
                android.util.Log.d("SemanticIndexing", "Reusing existing valid embedding for media: ${item.id}")
                return@withContext mutex.withLock {
                    if (!isMediaActive(item.id)) {
                        android.util.Log.d("SemanticIndexing", "Skipping reuse for media ${item.id} because it was deleted.")
                        return@withLock EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Media item deleted")
                    }
                    candidateRetriever.onRepresentationAdded(existing)
                    EmbeddingResult.Success(existing)
                }
            }

            // 4. Generate new neural embedding (run outside lock to avoid blocking other media)
            val result = embeddingProvider.generateEmbedding(
                mediaId = item.id,
                input = SemanticInput.Text(document),
                sourceDataHash = sourceHash
            )

            // 5. On success, persist and notify retriever for index update under per-media mutex
            if (result is EmbeddingResult.Success) {
                return@withContext mutex.withLock {
                    if (!isMediaActive(item.id)) {
                        android.util.Log.d("SemanticIndexing", "Skipping persistence for media ${item.id} because it was deleted during inference.")
                        return@withLock EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Media item deleted during inference")
                    }
                    val representation = result.representation.copy(documentVersion = currentDocVersion)
                    repository.saveRepresentation(representation)
                    candidateRetriever.onRepresentationAdded(representation)
                    result
                }
            }

            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INFERENCE_ERROR,
                message = "Indexing failed for media ${item.id}: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun indexBatch(items: List<MediaItem>) {
        items.forEach { item ->
            indexMediaItem(item)
        }
    }

    override suspend fun <T> withMediaLock(mediaId: String, block: suspend () -> T): T {
        val mutex = mediaLocks.getOrPut(mediaId) { Mutex() }
        return mutex.withLock { block() }
    }

    private fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
