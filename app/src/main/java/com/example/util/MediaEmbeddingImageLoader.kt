package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Higher-resolution photo decode for visual embeddings (See Similar / Visual Search).
 * Avoids low-quality system thumbnails that cause weak photo matching.
 */
object MediaEmbeddingImageLoader {
    private const val TAG = "MediaEmbeddingImageLoader"
    private const val MAX_EDGE = 1024

    suspend fun loadFullImageForEmbedding(context: Context, uriString: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (uriString.isBlank()) return@withContext null
            val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
            val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
            if (!MediaCompatibility.isSupportedPhoto(mimeType, uriString)) return@withContext null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return@withContext null
                var sample = 1
                val longEdge = maxOf(w, h)
                while (longEdge / sample > MAX_EDGE) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0) bitmap else null
            } catch (e: Exception) {
                Log.w(TAG, "Full image load failed for $uriString: ${e.message}")
                null
            }
        }
}
