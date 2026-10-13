package com.example.data.semantic

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * MobileCLIP-S0 image encoder with 85% center-crop preprocessing.
 */
class MobileCLIPEmbeddingProvider(
    private val engine: MobileCLIPInferenceEngine
) : EmbeddingProvider {

    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "mobileclip-s0",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL,
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32,
        artifactHash = "sha256:mobileclip-s0-v1"
    )

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        SemanticRepresentationType.VISUAL
    )

    override fun isReady(): Boolean = engine.isLoaded()

    companion object {
        private val globalInferenceSemaphore = kotlinx.coroutines.sync.Semaphore(1)
        private const val CENTER_CROP_FRACTION = 0.85f
        private val threadLocalPixelArray = ThreadLocal.withInitial { IntArray(256 * 256) }
        private val threadLocalFloatBuffer = ThreadLocal.withInitial { FloatBuffer.allocate(3 * 256 * 256) }
    }

    override suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.MODEL_UNAVAILABLE,
                "MobileCLIP inference engine not ready."
            )
        }
        val bitmap = when (input) {
            is SemanticInput.ExplicitBitmap -> input.bitmap
            else -> return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.UNSUPPORTED_TYPE,
                "MobileCLIP provider only supports ExplicitBitmap input in this phase."
            )
        }
        return@withContext try {
            globalInferenceSemaphore.withPermit {
                android.util.Log.i("QUERY_EMBEDDING", "CLIP_IMAGE_INFER_START: mediaId=$mediaId bitmap=${bitmap.width}x${bitmap.height}")
                val preprocStart = System.nanoTime()
                val processedBuffer = preprocessBitmap(bitmap)
                val preprocMs = (System.nanoTime() - preprocStart) / 1_000_000

                val inferStart = System.nanoTime()
                val rawEmbedding = engine.infer(processedBuffer, 256, 256)
                val inferMs = (System.nanoTime() - inferStart) / 1_000_000

                val nativeHeap = android.os.Debug.getNativeHeapAllocatedSize()
                android.util.Log.i("VisualIndexingPerf", "PERF_TIMING: token=${mediaId.hashCode()} preprocMs=$preprocMs inferMs=$inferMs nativeHeapBytes=$nativeHeap")

                val normalizedEmbedding = VectorMath.l2Normalize(rawEmbedding)
                android.util.Log.i("QUERY_EMBEDDING", "CLIP_IMAGE_INFER_SUCCESS: dim=${normalizedEmbedding.size}")
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
                "Failed to generate MobileCLIP embedding: ${e.message}",
                e
            )
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val targetSize = 256
        val cropSide = (minOf(bitmap.width, bitmap.height) * CENTER_CROP_FRACTION).toInt().coerceAtLeast(1)
        val cropX = ((bitmap.width - cropSide) / 2).coerceAtLeast(0)
        val cropY = ((bitmap.height - cropSide) / 2).coerceAtLeast(0)
        val cropped = try {
            Bitmap.createBitmap(bitmap, cropX, cropY, cropSide, cropSide)
        } catch (_: Exception) {
            bitmap
        }
        val scaledBitmap = if (cropped.width != targetSize || cropped.height != targetSize) {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        } else {
            cropped
        }
        val totalPixels = targetSize * targetSize
        val pixels = threadLocalPixelArray.get() ?: IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        val buffer = threadLocalFloatBuffer.get() ?: FloatBuffer.allocate(3 * totalPixels)
        buffer.clear()
        buffer.rewind()
        val backingArray = buffer.array()
        val gOffset = totalPixels
        val bOffset = 2 * totalPixels
        for (i in 0 until totalPixels) {
            val px = pixels[i]
            backingArray[i] = ((px shr 16) and 0xFF) / 255.0f
            backingArray[gOffset + i] = ((px shr 8) and 0xFF) / 255.0f
            backingArray[bOffset + i] = (px and 0xFF) / 255.0f
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
