package com.example.data.semantic

/**
 * Specialized retriever for MobileCLIP visual embeddings.
 */
interface MobileCLIPVisualRetriever {
    fun isReady(): Boolean

    suspend fun retrieveVisualCandidates(
        query: String,
        topK: Int,
        minSimilarity: Float
    ): List<RankedChannelItem>

    suspend fun retrieveVisualCandidates(
        queryVector: FloatArray,
        topK: Int,
        minSimilarity: Float
    ): List<RankedChannelItem>
}

class DefaultMobileCLIPVisualRetriever(
    private val visualSearchService: SemanticSearchService
) : MobileCLIPVisualRetriever, com.example.data.intelligence.VisualRetrievalProvider {

    override fun isReady(): Boolean = visualSearchService.isReady()

    override suspend fun retrieveVisualCandidates(
        query: String,
        topK: Int,
        minSimilarity: Float
    ): List<RankedChannelItem> {
        android.util.Log.i("VISUAL_RETRIEVAL", "RETRIEVE_START: query=\"$query\" topK=$topK")
        // Sep-3 style recall floor for visual retrieval (0.15f)
        val calibratedMinSim = if (minSimilarity < 0) 0.15f else minSimilarity
        val result = try {
            visualSearchService.search(
                query = query,
                topK = topK,
                minSimilarity = calibratedMinSim,
                targetType = SemanticRepresentationType.VISUAL
            )
        } catch (e: Exception) {
            android.util.Log.e("VISUAL_RETRIEVAL", "RETRIEVE_FAILURE: error=${e.message}")
            return emptyList()
        }
        if (!result.isSuccess) {
            android.util.Log.w("VISUAL_RETRIEVAL", "RETRIEVE_FAILURE: error=${result.errorMessage}")
            return emptyList()
        }
        android.util.Log.i("VISUAL_RETRIEVAL", "RETRIEVE_COMPLETE: found=${result.candidates.size}")
        return result.candidates.mapIndexed { index, candidate ->
            RankedChannelItem(
                mediaId = candidate.mediaId,
                rawScore = candidate.similarityScore,
                rank = index + 1,
                metadata = mapOf(
                    "representationType" to candidate.type.name,
                    "modelId" to candidate.modelDescriptor.modelId
                )
            )
        }
    }

    override suspend fun retrieveVisualCandidates(
        queryVector: FloatArray,
        topK: Int,
        minSimilarity: Float
    ): List<RankedChannelItem> {
        android.util.Log.i("VISUAL_RETRIEVAL", "RETRIEVE_VECTOR_START: dim=${queryVector.size}")
        val calibratedMinSim = if (minSimilarity < 0) 0.15f else minSimilarity
        val result = try {
            visualSearchService.search(
                queryVector = queryVector,
                queryLabel = "Visual Reference",
                topK = topK,
                minSimilarity = calibratedMinSim,
                targetType = SemanticRepresentationType.VISUAL
            )
        } catch (e: Exception) {
            android.util.Log.e("VISUAL_RETRIEVAL", "RETRIEVE_VECTOR_FAILURE: error=${e.message}")
            return emptyList()
        }
        if (!result.isSuccess) {
            android.util.Log.w("VISUAL_RETRIEVAL", "RETRIEVE_VECTOR_FAILURE: error=${result.errorMessage}")
            return emptyList()
        }
        android.util.Log.i("VISUAL_RETRIEVAL", "RETRIEVE_VECTOR_COMPLETE: found=${result.candidates.size}")
        return result.candidates.mapIndexed { index, candidate ->
            RankedChannelItem(
                mediaId = candidate.mediaId,
                rawScore = candidate.similarityScore,
                rank = index + 1,
                metadata = mapOf(
                    "representationType" to candidate.type.name,
                    "modelId" to candidate.modelDescriptor.modelId
                )
            )
        }
    }
}
