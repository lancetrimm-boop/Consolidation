package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min

object MediaThumbnailFetcher {
    private const val TAG = "MediaThumbnailFetcher"
    private const val THUMBNAIL_DIR = "thumbnails"
    private const val MAX_CONCURRENT_EXTRACTIONS = 2
    private const val TARGET_THUMBNAIL_SIZE = 512
    private const val EMBEDDING_MAX_EDGE = 1024
    private const val BLACK_FRAME_LUMINANCE_THRESHOLD = 15.0
    private const val BLACK_FRAME_PERCENTAGE_THRESHOLD = 0.98f

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }
    private val extractionSemaphore = Semaphore(MAX_CONCURRENT_EXTRACTIONS)

    suspend fun getThumbnail(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        memoryCache.get(uriString)?.let { return@withContext it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uriString.startsWith("content://")) {
            val cacheKey = generateCacheKey(uriString, -1L, TARGET_THUMBNAIL_SIZE)
            val cacheFile = getCacheFile(context, cacheKey)
            if (cacheFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (isValidBitmap(bitmap) && !isBlackFrame(bitmap!!)) {
                        memoryCache.put(uriString, bitmap)
                        return@withContext bitmap
                    }
                } catch (_: Exception) {}
            }
            try {
                val uri = Uri.parse(uriString)
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(TARGET_THUMBNAIL_SIZE, TARGET_THUMBNAIL_SIZE), null)
                if (isValidBitmap(bitmap) && !isBlackFrame(bitmap)) {
                    saveToDiskCache(cacheFile, bitmap)
                    memoryCache.put(uriString, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadThumbnail failed for $uriString: ${e.message}")
            }
        }
        val bitmap = getFrameAtTime(context, uriString, 2_000_000L)
        if (bitmap != null) memoryCache.put(uriString, bitmap)
        return@withContext bitmap
    }

    /**
     * Higher-res photo decode for visual embeddings (avoids weak system thumbnails).
     */
    suspend fun loadFullImageForEmbedding(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        if (!MediaCompatibility.isSupportedPhoto(context, uriString)) return@withContext null
        val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            } catch (_: Exception) {}
            if (bounds.outWidth <= 0 && uriString.startsWith("/")) {
                BitmapFactory.decodeFile(uriString, bounds)
            }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null
            var sample = 1
            val longEdge = maxOf(w, h)
            while (longEdge / sample > EMBEDDING_MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            } catch (_: Exception) { null }
                ?: if (uriString.startsWith("/")) BitmapFactory.decodeFile(uriString, opts) else null

            if (isValidBitmap(bitmap)) bitmap else null
        } catch (e: Exception) {
            Log.w(TAG, "loadFullImageForEmbedding failed for $uriString: ${e.message}")
            null
        }
    }

    suspend fun getFrameAtTime(context: Context, uriString: String, timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        val cacheKey = generateCacheKey(uriString, timeUs, TARGET_THUMBNAIL_SIZE)
        val cacheFile = getCacheFile(context, cacheKey)
        memoryCache.get(cacheKey)?.let { return@withContext it }
        if (cacheFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (isValidBitmap(bitmap)) {
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            } catch (_: Exception) {}
        }
        ensureActive()
        return@withContext try {
            if (!isActive) return@withContext null
            extractionSemaphore.withPermit {
                ensureActive()
                var bitmap = extractFrame(context, uriString, timeUs)
                if (isValidBitmap(bitmap) && isBlackFrame(bitmap!!)) {
                    val durationMs = getDuration(context, uriString)
                    if (durationMs > 0) {
                        val retry1Us = min(durationMs * 500L, 10_000_000L)
                        if (retry1Us > timeUs) {
                            val retryBitmap = extractFrame(context, uriString, retry1Us)
                            if (isValidBitmap(retryBitmap)) {
                                if (!isBlackFrame(retryBitmap!!)) bitmap = retryBitmap
                                else {
                                    val retry2Us = durationMs * 200L
                                    if (retry2Us > 0 && retry2Us != retry1Us && retry2Us != timeUs) {
                                        val finalBitmap = extractFrame(context, uriString, retry2Us)
                                        if (isValidBitmap(finalBitmap)) bitmap = finalBitmap
                                    }
                                }
                            }
                        }
                    }
                }
                if (isValidBitmap(bitmap)) saveToDiskCache(cacheFile, bitmap!!)
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame extraction failed for $uriString: ${e.message}")
            null
        }
    }

    private fun extractFrame(context: Context, uriString: String, timeUs: Long): Bitmap? {
        val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        if (MediaCompatibility.isSupportedPhoto(mimeType, uriString)) {
            return try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Photo decoding failed for $uriString: ${e.message}")
                null
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://") || uriString.startsWith("/")) {
                retriever.setDataSource(context, uri)
            } else retriever.setDataSource(uriString)
            val rotStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) } catch (_: Exception) { null }
            val rotation = rotStr?.toIntOrNull() ?: 0

            val wStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) } catch (_: Exception) { null }
            val hStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) } catch (_: Exception) { null }
            val nativeW = wStr?.toIntOrNull() ?: 0
            val nativeH = hStr?.toIntOrNull() ?: 0

            val effectiveW = if (rotation == 90 || rotation == 270) nativeH else nativeW
            val effectiveH = if (rotation == 90 || rotation == 270) nativeW else nativeH

            val (targetW, targetH) = if (effectiveW > 0 && effectiveH > 0) {
                val maxEdge = maxOf(effectiveW, effectiveH).toFloat()
                val scale = TARGET_THUMBNAIL_SIZE.toFloat() / maxEdge
                val tW = (effectiveW * scale).toInt().coerceAtLeast(64)
                val tH = (effectiveH * scale).toInt().coerceAtLeast(64)
                tW to tH
            } else {
                TARGET_THUMBNAIL_SIZE to TARGET_THUMBNAIL_SIZE
            }

            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: try { retriever.frameAtTime } catch (_: Exception) { null }
            if (isValidBitmap(frame)) {
                if (rotation != 0 && frame!!.width != effectiveW && frame.height != effectiveH) {
                    MediaOrientationUtils.rotateBitmapIfNeeded(frame, rotation)
                } else {
                    frame
                }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for $uriString: ${e.message}")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun isBlackFrame(bitmap: Bitmap): Boolean {
        val sampleStep = 24
        var darkPixels = 0
        var totalSamples = 0
        for (x in 0 until bitmap.width step sampleStep) {
            for (y in 0 until bitmap.height step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if ((0.299 * r + 0.587 * g + 0.114 * b) < BLACK_FRAME_LUMINANCE_THRESHOLD) darkPixels++
                totalSamples++
            }
        }
        return totalSamples > 0 && (darkPixels.toFloat() / totalSamples) > BLACK_FRAME_PERCENTAGE_THRESHOLD
    }

    private fun getDuration(context: Context, uriString: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
            if (uriString.startsWith("content://") || uriString.startsWith("file://") || uriString.startsWith("/")) {
                retriever.setDataSource(context, uri)
            } else retriever.setDataSource(uriString)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (_: Exception) { 0L } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun getNativeDimensions(context: Context, uriString: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext 0 to 0
        val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        if (MediaCompatibility.isSupportedPhoto(mimeType, uriString)) {
            try {
                var width = 0
                var height = 0
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                var decoded = false
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    decoded = options.outWidth > 0 && options.outHeight > 0
                } catch (e: Exception) {
                    Log.w(TAG, "openInputStream failed for photo $uriString: ${e.message}")
                }
                if (!decoded && uriString.startsWith("/")) {
                    try {
                        BitmapFactory.decodeFile(uriString, options)
                        decoded = options.outWidth > 0 && options.outHeight > 0
                    } catch (e: Exception) {
                        Log.w(TAG, "decodeFile failed for photo $uriString: ${e.message}")
                    }
                }
                if (decoded) {
                    width = options.outWidth
                    height = options.outHeight
                    try {
                        val inputStream = try { context.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
                            ?: if (uriString.startsWith("/")) File(uriString).inputStream() else null
                        if (inputStream != null) {
                            inputStream.use { stream ->
                                val exif = ExifInterface(stream)
                                val orientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                                val rotationDegrees = when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                                if (MediaOrientationUtils.shouldSwapDimensions(rotationDegrees)) {
                                    val temp = width
                                    width = height
                                    height = temp
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "EXIF extraction failed for $uriString: ${e.message}")
                    }
                    return@withContext width to height
                }
            } catch (e: Exception) {
                Log.w(TAG, "Photo native dimension resolution failed for $uriString: ${e.message}")
            }
        }
        val retriever = MediaMetadataRetriever()
        return@withContext try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://") || uriString.startsWith("/")) {
                retriever.setDataSource(context, uri)
            } else retriever.setDataSource(uriString)
            val rotStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) } catch (_: Exception) { null }
            val rotation = rotStr?.toIntOrNull() ?: 0
            val wStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) } catch (_: Exception) { null }
            val hStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) } catch (_: Exception) { null }
            val rawW = wStr?.toIntOrNull() ?: 0
            val rawH = hStr?.toIntOrNull() ?: 0
            val swap = MediaOrientationUtils.shouldSwapDimensions(rotation)
            val w = if (swap) rawH else rawW
            val h = if (swap) rawW else rawH
            w to h
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever native dimension resolution failed for $uriString: ${e.message}")
            0 to 0
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun isValidBitmap(bitmap: Bitmap?) =
        bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0

    private fun saveToDiskCache(file: File, bitmap: Bitmap) {
        val tempFile = File(file.parent, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(tempFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            if (!tempFile.renameTo(file)) tempFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save thumbnail: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun getCacheFile(context: Context, key: String) =
        File(File(context.cacheDir, THUMBNAIL_DIR), "$key.jpg")

    private fun generateCacheKey(uriString: String, timeUs: Long, size: Int): String {
        val input = "${uriString}_${timeUs}_${size}"
        return try {
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { input.hashCode().toString() }
    }

    fun removeThumbnail(uriString: String) { memoryCache.remove(uriString) }

    fun clearDiskCache(context: Context) {
        try {
            val dir = File(context.cacheDir, THUMBNAIL_DIR)
            if (dir.exists()) dir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear disk cache: ${e.message}")
        }
    }
}

object MediaCompatibility {
    fun isSupportedVideo(mimeType: String?, uriString: String): Boolean {
        if (mimeType != null && mimeType.startsWith("video/")) return true
        val lower = uriString.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".3gp") || lower.contains("video") ||
                lower.startsWith("content://media/external/video")
    }

    fun isSupportedPhoto(mimeType: String?, uriString: String): Boolean {
        if (mimeType != null && mimeType.startsWith("image/")) return true
        val lower = uriString.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".heic") || lower.contains("image") ||
                lower.startsWith("content://media/external/images")
    }

    fun isSupportedPhoto(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        if (isSupportedPhoto(mimeType, uriString)) return true

        if (uriString.startsWith("content://") || uriString.startsWith("file://") || uriString.startsWith("/")) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, bounds) 
                }
                if (bounds.outWidth > 0 && bounds.outHeight > 0) return true
            } catch (_: Exception) {}
            if (uriString.startsWith("/")) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(uriString, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) return true
                } catch (_: Exception) {}
            }
        }
        return false
    }
}
