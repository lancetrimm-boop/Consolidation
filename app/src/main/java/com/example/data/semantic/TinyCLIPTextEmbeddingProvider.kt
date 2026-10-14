package com.example.data.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Isolated embedding provider for TinyCLIP-ViT-8M/16 text encoder.
 * Shares descriptor space `tinyclip-vit-8m-16` with [TinyCLIPEmbeddingProvider].
 */
class TinyCLIPTextEmbeddingProvider(
    private val engine: MobileCLIPTextInferenceEngine,
    private val tokenizer: ClipBpeTokenizer
) : EmbeddingProvider {

    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "tinyclip-vit-8m-16",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL, // Aligned with visual retrieval space
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32,
        artifactHash = "sha256:5a33d89d807c79ef38737031d4fa9bffa0e41bac4bec4e165af5f8ddefbdc3b7"
    )

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        SemanticRepresentationType.CONTENT,
        SemanticRepresentationType.VISUAL
    )

    override fun isReady(): Boolean = engine.isLoaded()

    override suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.MODEL_UNAVAILABLE,
                "TinyCLIP text engine not ready."
            )
        }

        val text = when (input) {
            is SemanticInput.Text -> input.text
            is SemanticInput.Tokens -> input.tokens.joinToString(" ")
            else -> return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.UNSUPPORTED_TYPE,
                "TinyCLIP text provider only supports Text or Tokens input."
            )
        }

        return@withContext try {
            val inputIds = tokenizer.tokenize(text)
            val rawEmbedding = engine.infer(inputIds)
            val normalizedEmbedding = VectorMath.l2Normalize(rawEmbedding)

            val representationId = if (mediaId.startsWith("query_")) {
                mediaId
            } else {
                "sem_${mediaId}_text_${descriptor.modelId}_v${descriptor.modelVersion}"
            }

            val representation = SemanticRepresentation(
                id = representationId,
                mediaId = mediaId,
                type = input.targetType,
                modelDescriptor = descriptor,
                dimensionality = descriptor.dimensionality,
                vector = normalizedEmbedding,
                sourceDataHash = sourceDataHash,
                confidence = 1.0f
            )

            EmbeddingResult.Success(representation)
        } catch (e: Exception) {
            EmbeddingResult.Failure(
                EmbeddingErrorCode.INFERENCE_ERROR,
                "Failed to generate TinyCLIP text embedding: ${e.message}",
                e
            )
        }
    }

    override fun close() {
        engine.close()
    }
}
