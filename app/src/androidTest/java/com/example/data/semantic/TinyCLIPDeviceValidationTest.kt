package com.example.data.semantic

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.ModelAssetLoader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class TinyCLIPDeviceValidationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        private const val TAG = "TinyCLIPDeviceTest"
        private const val EXPECTED_VISION_SHA256 = "b78e25e17397f1d2f64e1da80c70bd922fd5bc4a2bfede31465c6f6913e3389e"
        private const val EXPECTED_TEXT_SHA256 = "83fc955313aa7b09d7c4beca940d563719c3086dad0fe367f28a14aa5b2aee72"
        private const val EXPECTED_VISION_SIZE = 34_105_241L
        private const val EXPECTED_TEXT_SIZE = 61_020_224L
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testTinyCLIPFullDeviceValidation() {
        val runtime = Runtime.getRuntime()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        // 1. Delete ONLY stale extracted TinyCLIP ONNX files if present, preserving MobileCLIP/MiniLM
        val auraModelsDir = File(context.filesDir, "aura_models")
        val staleVisionFile = File(auraModelsDir, "tinyclip_vision.onnx")
        val staleTextFile = File(auraModelsDir, "tinyclip_text.onnx")
        val staleVisionData = File(auraModelsDir, "tinyclip_vision.onnx.data")
        val staleTextData = File(auraModelsDir, "tinyclip_text.onnx.data")

        if (staleVisionFile.exists()) staleVisionFile.delete()
        if (staleTextFile.exists()) staleTextFile.delete()
        if (staleVisionData.exists()) staleVisionData.delete()
        if (staleTextData.exists()) staleTextData.delete()

        // 2. Extract using normal ModelAssetLoader path
        val visionLoadStart = System.currentTimeMillis()
        val visionPath = ModelAssetLoader.getLocalPath(context, "models/tinyclip_vision.onnx")
        val visionExtractedFile = File(visionPath)

        val textPath = ModelAssetLoader.getLocalPath(context, "models/tinyclip_text.onnx")
        val textExtractedFile = File(textPath)

        val extractedVisionSize = visionExtractedFile.length()
        val extractedVisionSha256 = computeSha256(visionExtractedFile)
        val extractedTextSize = textExtractedFile.length()
        val extractedTextSha256 = computeSha256(textExtractedFile)

        Log.i(TAG, "Extracted Vision Path: $visionPath ($extractedVisionSize bytes, sha256=$extractedVisionSha256)")
        Log.i(TAG, "Extracted Text Path: $textPath ($extractedTextSize bytes, sha256=$extractedTextSha256)")

        assertEquals(EXPECTED_VISION_SIZE, extractedVisionSize)
        assertEquals(EXPECTED_VISION_SHA256, extractedVisionSha256)
        assertEquals(EXPECTED_TEXT_SIZE, extractedTextSize)
        assertEquals(EXPECTED_TEXT_SHA256, extractedTextSha256)

        // 3. Initialize Vision Engine from normal extracted file path
        val visionEngine = OnnxRuntimeMobileCLIPInferenceEngine(modelPath = visionPath)
        val visionLoadTime = System.currentTimeMillis() - visionLoadStart
        val heapAfterVisionLoad = runtime.totalMemory() - runtime.freeMemory()

        // 4. Preprocess 224x224 RGB test bitmap with CLIP mean/std normalization
        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        for (x in 0 until 224) {
            for (y in 0 until 224) {
                testBitmap.setPixel(x, y, Color.rgb(200, 100, 50)) // Orange/Sunset color
            }
        }

        val buffer = FloatBuffer.allocate(3 * 224 * 224)
        val backingArray = buffer.array()
        val totalPixels = 224 * 224
        val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        val pixels = IntArray(totalPixels)
        testBitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        for (i in 0 until totalPixels) {
            val px = pixels[i]
            val rNorm = ((px shr 16) and 0xFF) / 255.0f
            val gNorm = ((px shr 8) and 0xFF) / 255.0f
            val bNorm = (px and 0xFF) / 255.0f
            backingArray[i] = (rNorm - MEAN[0]) / STD[0]
            backingArray[totalPixels + i] = (gNorm - MEAN[1]) / STD[1]
            backingArray[2 * totalPixels + i] = (bNorm - MEAN[2]) / STD[2]
        }
        buffer.rewind()

        // First Vision Inference
        val firstVisionStart = System.currentTimeMillis()
        val rawVisionVector = visionEngine.infer(buffer, 224, 224)
        val firstVisionMs = System.currentTimeMillis() - firstVisionStart
        val visionVector = VectorMath.l2Normalize(rawVisionVector)

        assertEquals(512, visionVector.size)
        for (v in visionVector) {
            assertFalse("Vision vector element is NaN", v.isNaN())
            assertFalse("Vision vector element is Infinite", v.isInfinite())
        }
        assertEquals(1.0f, VectorMath.magnitude(visionVector), 1e-4f)

        // Warm 50 passes Vision
        val visionPasses = DoubleArray(50)
        for (i in 0 until 50) {
            buffer.rewind()
            val passStart = System.nanoTime()
            val raw = visionEngine.infer(buffer, 224, 224)
            VectorMath.l2Normalize(raw)
            visionPasses[i] = (System.nanoTime() - passStart) / 1_000_000.0
        }
        visionPasses.sort()
        val visionMeanMs = visionPasses.average()
        val visionP95Ms = visionPasses[47]

        // 5. Initialize Text Engine
        val textLoadStart = System.currentTimeMillis()
        val textEngine = OnnxRuntimeMobileCLIPTextInferenceEngine(modelPath = textPath)
        val textLoadTime = System.currentTimeMillis() - textLoadStart

        val vocabJson = context.assets.open("models/mobileclip_vocab.json").use { it.bufferedReader().readText() }
        val mergesTxt = context.assets.open("models/mobileclip_merges.txt").use { it.bufferedReader().readText() }
        val tokenizer = ClipBpeTokenizer.fromAssets(vocabJson, mergesTxt)
        val inputIds = tokenizer.tokenize("sunset over water")

        // First Text Inference
        val firstTextStart = System.currentTimeMillis()
        val rawTextVector = textEngine.infer(inputIds)
        val firstTextMs = System.currentTimeMillis() - firstTextStart
        val textVector = VectorMath.l2Normalize(rawTextVector)

        assertEquals(512, textVector.size)
        for (v in textVector) {
            assertFalse("Text vector element is NaN", v.isNaN())
            assertFalse("Text vector element is Infinite", v.isInfinite())
        }
        assertEquals(1.0f, VectorMath.magnitude(textVector), 1e-4f)

        // Warm 50 passes Text
        val textPasses = DoubleArray(50)
        for (i in 0 until 50) {
            val passStart = System.nanoTime()
            val raw = textEngine.infer(inputIds)
            VectorMath.l2Normalize(raw)
            textPasses[i] = (System.nanoTime() - passStart) / 1_000_000.0
        }
        textPasses.sort()
        val textMeanMs = textPasses.average()
        val textP95Ms = textPasses[47]

        val heapAfterWarm = runtime.totalMemory() - runtime.freeMemory()

        // 6. Cross-modal Cosine Similarity
        val similarity = VectorMath.cosineSimilarity(visionVector, textVector)
        assertTrue("Cosine similarity must be finite", !similarity.isNaN() && !similarity.isInfinite())
        assertTrue("Cosine similarity out of valid range [-1, 1]", similarity in -1.001f..1.001f)

        // 7. Descriptor Isolation
        val tinyClipDesc = EmbeddingModelDescriptor("tinyclip-vit-8m-16", 1, 512, SemanticRepresentationType.VISUAL, ModelRuntimeFormat.ONNX, QuantizationType.NONE_FP32, "sha256:$EXPECTED_VISION_SHA256")
        val mobileClipDesc = EmbeddingModelDescriptor("mobileclip-s0", 1, 512, SemanticRepresentationType.VISUAL, ModelRuntimeFormat.ONNX, QuantizationType.NONE_FP32, "sha256:mobileclip-s0-v1")
        val miniLMDesc = EmbeddingModelDescriptor("all-minilm-l6-v2", 1, 384, SemanticRepresentationType.CONTENT, ModelRuntimeFormat.ONNX, QuantizationType.NONE_FP32, "sha256:all-minilm-l6-v2-v1")

        assertFalse(tinyClipDesc.isCompatibleWith(mobileClipDesc))
        assertFalse(tinyClipDesc.isCompatibleWith(miniLMDesc))

        Log.i(TAG, "=== DEVICE VALIDATION TELEMETRY ===")
        Log.i(TAG, "Heap Bytes Before: $heapBefore, After Vision Load: $heapAfterVisionLoad, After Warm: $heapAfterWarm")
        Log.i(TAG, "Vision Load Time: ${visionLoadTime}ms, First Infer: ${firstVisionMs}ms, Warm Mean: ${String.format("%.2f", visionMeanMs)}ms, P95: ${String.format("%.2f", visionP95Ms)}ms")
        Log.i(TAG, "Text Load Time: ${textLoadTime}ms, First Infer: ${firstTextMs}ms, Warm Mean: ${String.format("%.2f", textMeanMs)}ms, P95: ${String.format("%.2f", textP95Ms)}ms")
        Log.i(TAG, "Cross-Modal Cosine Sim: $similarity")
        Log.i(TAG, "===================================")

        visionEngine.close()
        textEngine.close()
    }
}
