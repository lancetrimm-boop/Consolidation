package com.example.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.example.data.entitlement.EntitlementRepository
import com.example.data.entitlement.ProFeature
import com.example.data.MediaItem as AuraMediaItem
import kotlinx.coroutines.*
import java.io.File

/**
 * Service for exporting Aura Moments as MP4 video files.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MomentExporter(
    private val context: Context,
    private val entitlementRepository: EntitlementRepository,
    private val engine: MomentEncodingEngine? = null
) {
    private val TAG = "MomentExporter"
    private val PHOTO_DURATION_US = 4_000_000L
    private val VIDEO_MAX_DURATION_MS = 15_000L

    private val encodingEngine: MomentEncodingEngine by lazy {
        engine ?: Media3MomentEncodingEngine(context)
    }

    sealed class ExportState {
        object Idle : ExportState()
        data class Exporting(val progress: Int) : ExportState()
        data class Success(val file: File) : ExportState()
        data class Failed(val message: String) : ExportState()
        object NotEntitled : ExportState()
        object Cancelled : ExportState()
    }

    suspend fun exportMoment(
        items: List<AuraMediaItem>,
        onProgress: (Int) -> Unit = {}
    ): ExportState = withContext(Dispatchers.IO) {
        if (!entitlementRepository.isFeatureAvailable(ProFeature.MOMENTS_EXPORT)) {
            return@withContext ExportState.NotEntitled
        }

        if (items.isEmpty()) {
            return@withContext ExportState.Failed("Moment is empty")
        }

        val outputDir = File(context.cacheDir, "aura_moments")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, "Moment_${System.currentTimeMillis()}.mp4")
        
        try {
            val editedMediaItems = items.map { auraItem ->
                val uriStr = auraItem.uriPath.ifBlank { auraItem.imageUrl }
                val isPhoto = auraItem.mediaType == "PHOTO"
                val uri = Uri.parse(uriStr)
                
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(uri)
                
                if (isPhoto) {
                    // CRITICAL: Must set image mime type for ImageAssetLoader to be selected
                    mediaItemBuilder.setMimeType(MimeTypes.IMAGE_JPEG)
                    mediaItemBuilder.setImageDurationMs(PHOTO_DURATION_US / 1000)
                }
                
                if (auraItem.mediaType == "VIDEO" && auraItem.durationMs > VIDEO_MAX_DURATION_MS) {
                    mediaItemBuilder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(VIDEO_MAX_DURATION_MS)
                            .build()
                    )
                }
                
                val mediaItem = mediaItemBuilder.build()
                val editedBuilder = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(isPhoto)
                
                if (isPhoto) {
                    // CRITICAL: Must set frame rate for ImageAssetLoader
                    editedBuilder.setFrameRate(30)
                }
                
                editedBuilder.build()
            }

            val sequence = EditedMediaItemSequence(editedMediaItems)
            val composition = Composition.Builder(listOf(sequence)).build()

            val result = encodingEngine.encode(composition, outputFile, onProgress)
            
            if (result.isSuccess) {
                // Save to MediaStore & Register in Room DB
                try {
                    val filename = "AuraMoment_${System.currentTimeMillis()}.mp4"
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/AuraMoments")
                            put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }

                    val mediaStoreUri = context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (mediaStoreUri != null) {
                        context.contentResolver.openOutputStream(mediaStoreUri)?.use { out ->
                            outputFile.inputStream().use { it.copyTo(out) }
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                            context.contentResolver.update(mediaStoreUri, contentValues, null, null)
                        }
                    }

                    val finalUriStr = mediaStoreUri?.toString() ?: Uri.fromFile(outputFile).toString()
                    val entity = com.example.data.db.MediaEntity(
                        id = "aura_moment_${System.currentTimeMillis()}",
                        title = "Aura Moment ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                        mediaType = "VIDEO",
                        year = 2026,
                        duration = "15s",
                        durationMs = 15000L,
                        genre = "Aura Generated Moments",
                        imageUrl = finalUriStr,
                        rating = 5.0f,
                        category = "User Media",
                        aiSummary = "Aura AI Generated Slideshow Moment",
                        moodTagsJson = "User Media,Moment",
                        uriPath = finalUriStr,
                        dateAdded = System.currentTimeMillis(),
                        sizeBytes = outputFile.length()
                    )
                    com.example.data.MediaRepository.getInstance(context).addMediaEntity(entity)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish exported moment to MediaStore/Room", e)
                }

                ExportState.Success(outputFile)
            } else {
                outputFile.delete()
                ExportState.Failed(result.exceptionOrNull()?.message ?: "Encoding failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            if (outputFile.exists()) outputFile.delete()
            ExportState.Failed(e.message ?: "Unexpected error")
        }
    }
}
