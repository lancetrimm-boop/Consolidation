package com.example.data.semantic

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Isolated embedding provider for TinyCLIP-ViT-8M/16 visual frame encoder.
 * Applies standard CLIP 224x224 RGB resize/crop and channel mean/std normalization.
 */
class TinyCLIPEmbeddingProvider(
    private val engine: MobileCLIPInferenceEngine
) : EmbeddingProvider {

    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "tinyclip-vit-8m-16",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL,
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32,
        artifactHash = "sha256:3340fcad5d58c6bbd7cafa0c0c179b927fbacb27fc8b78635c6497d98628f382"
    )

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        SemanticRepresentationType.VISUAL
    )

    override fun isReady(): Boolean = engine.isLoaded()

    companion object {
        private val globalInferenceSemaphore = kotlinx.coroutines.sync.Semaphore(1)
        private const val TARGET_SIZE = 224
        private val threadLocalPixelArray = ThreadLocal.withInitial { IntArray(224 * 224) }
        private val threadLocalFloatBuffer = ThreadLocal.withInitial { FloatBuffer.allocate(3 * 224 * 224) }

        // Standard CLIP normalization parameters
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }

    override suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.MODEL_UNAVAILABLE,
                "TinyCLIP vision engine not ready."
            )
        }
        val bitmap = when (input) {
            is SemanticInput.ExplicitBitmap -> input.bitmap
            else -> return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.UNSUPPORTED_TYPE,
                "TinyCLIP provider only supports ExplicitBitmap input."
            )
        }
        return@withContext try {
            globalInferenceSemaphore.withPermit {
                val processedBuffer = preprocessBitmap(bitmap)
                val rawEmbedding = engine.infer(processedBuffer, TARGET_SIZE, TARGET_SIZE)
                val normalizedEmbedding = VectorMath.l2Normalize(rawEmbedding)

                val representationId = "sem_${mediaId}_visual_${descriptor.modelId}_v${descriptor.modelVersion}"
                val representation = SemanticRepresentation(
                    id = representationId,
                    mediaId = mediaId,
                    type = SemanticRepresentationType.VISUAL,
                    modelDescriptor = descriptor,
                    dimensionality = descriptor.dimensionality,
                    vector = normalizedEmbedding,
                    sourceDataHash = sourceDataHash,
                    confidence = 1.0f
                )
                EmbeddingResult.Success(representation)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            EmbeddingResult.Failure(
                EmbeddingErrorCode.INFERENCE_ERROR,
                "Failed to generate TinyCLIP embedding: ${e.message}",
                e
            )
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val side = minOf(bitmap.width, bitmap.height)
        val cropX = (bitmap.width - side) / 2
        val cropY = (bitmap.height - side) / 2
        val cropped = try {
            Bitmap.createBitmap(bitmap, cropX, cropY, side, side)
        } catch (_: Exception) {
            bitmap
        }
        val scaledBitmap = if (cropped.width != TARGET_SIZE || cropped.height != TARGET_SIZE) {
            Bitmap.createScaledBitmap(cropped, TARGET_SIZE, TARGET_SIZE, true)
        } else {
            cropped
        }

        val totalPixels = TARGET_SIZE * TARGET_SIZE
        val pixels = threadLocalPixelArray.get() ?: IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, TARGET_SIZE, 0, 0, TARGET_SIZE, TARGET_SIZE)

        val buffer = threadLocalFloatBuffer.get() ?: FloatBuffer.allocate(3 * totalPixels)
        buffer.clear()
        buffer.rewind()
        val backingArray = buffer.array()

        val gOffset = totalPixels
        val bOffset = 2 * totalPixels

        for (i in 0 until totalPixels) {
            val px = pixels[i]
            val rNorm = ((px shr 16) and 0xFF) / 255.0f
            val gNorm = ((px shr 8) and 0xFF) / 255.0f
            val bNorm = (px and 0xFF) / 255.0f

            // Apply standard CLIP channel normalization: (x - mean) / std
            backingArray[i] = (rNorm - MEAN[0]) / STD[0]
            backingArray[gOffset + i] = (gNorm - MEAN[1]) / STD[1]
            backingArray[bOffset + i] = (bNorm - MEAN[2]) / STD[2]
        }

        if (scaledBitmap !== bitmap && scaledBitmap !== cropped) scaledBitmap.recycle()
        if (cropped !== bitmap) cropped.recycle()
        buffer.rewind()
        return buffer
    }

    override fun close() {
        engine.close()
    }
}
