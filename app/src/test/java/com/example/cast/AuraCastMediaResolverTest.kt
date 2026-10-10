package com.example.cast

import android.content.Context
import com.example.data.CompatibilityStatus
import com.example.data.ConversionStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuraCastMediaResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `rejects media failing Gate 1 or Gate 2 eligibility`() {
        val mockContext = mock(Context::class.java)
        val mockRepo = mock(MediaRepository::class.java)

        val unplayableItem = MediaItem(
            id = "unplayable1",
            title = "Corrupt Video.mp4",
            mediaType = "VIDEO",
            uriPath = "/storage/emulated/0/corrupt.mp4",
            compatibilityStatus = CompatibilityStatus.CORRUPT
        )

        `when`(mockRepo.isItemEligibleForCandidates(any())).thenReturn(false)
        `when`(mockRepo.isItemVisibleInLibrary(any())).thenReturn(false)

        val resolved = AuraCastMediaResolver.resolveForCast(mockContext, mockRepo, unplayableItem)
        assertNull("Ineligible media must be rejected by Cast resolver", resolved)
    }

    @Test
    fun `prefers converted file when status is CONVERTED and convertedUri is valid`() {
        val mockContext = mock(Context::class.java)
        val mockRepo = mock(MediaRepository::class.java)

        val convertedFile = tempFolder.newFile("converted_output.mp4").apply {
            writeBytes("converted data".toByteArray())
        }

        val convertedItem = MediaItem(
            id = "conv1",
            title = "Raw HEVC Video.mov",
            mediaType = "VIDEO",
            uriPath = "/storage/emulated/0/raw.mov",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            conversionStatus = ConversionStatus.CONVERTED,
            convertedUri = convertedFile.absolutePath
        )

        `when`(mockRepo.isItemEligibleForCandidates(any())).thenReturn(true)
        `when`(mockRepo.isItemVisibleInLibrary(any())).thenReturn(true)

        val resolved = AuraCastMediaResolver.resolveForCast(mockContext, mockRepo, convertedItem)
        assertNotNull(resolved)
        assertTrue(resolved!!.source is CastMediaSource.FileSource)

        val fileSource = resolved.source as CastMediaSource.FileSource
        assertEquals(convertedFile.absolutePath, fileSource.file.absolutePath)
    }

    @Test
    fun `fallbacks to original source if converted Uri is invalid`() {
        val mockContext = mock(Context::class.java)
        val mockRepo = mock(MediaRepository::class.java)

        val originalFile = tempFolder.newFile("original_valid.mp4").apply {
            writeBytes("original data".toByteArray())
        }

        val itemWithBadConverted = MediaItem(
            id = "conv_bad",
            title = "Valid Video.mp4",
            mediaType = "VIDEO",
            uriPath = originalFile.absolutePath,
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            conversionStatus = ConversionStatus.CONVERTED,
            convertedUri = "/non/existent/path/converted.mp4"
        )

        `when`(mockRepo.isItemEligibleForCandidates(any())).thenReturn(true)
        `when`(mockRepo.isItemVisibleInLibrary(any())).thenReturn(true)

        val resolved = AuraCastMediaResolver.resolveForCast(mockContext, mockRepo, itemWithBadConverted)
        assertNotNull(resolved)
        assertTrue(resolved!!.source is CastMediaSource.FileSource)

        val fileSource = resolved.source as CastMediaSource.FileSource
        assertEquals(originalFile.absolutePath, fileSource.file.absolutePath)
    }
}
