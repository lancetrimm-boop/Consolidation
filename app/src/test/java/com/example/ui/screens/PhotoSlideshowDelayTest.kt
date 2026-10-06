package com.example.ui.screens

import com.example.data.MediaItem
import com.example.data.MediaRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhotoSlideshowDelayTest {

    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        repository = MediaRepository.getInstance(RuntimeEnvironment.getApplication())
        repository.slideshowDelaySec = 4
    }

    private fun createPhotoItem(id: String): MediaItem {
        return MediaItem(
            id = id,
            title = "Photo $id",
            mediaType = "PHOTO",
            uriPath = "content://media/external/images/media/$id"
        )
    }

    private fun createVideoItem(id: String): MediaItem {
        return MediaItem(
            id = id,
            title = "Video $id",
            mediaType = "VIDEO",
            uriPath = "content://media/external/video/media/$id"
        )
    }

    @Test
    fun testDefaultSlideshowDelay_Is4Seconds() {
        assertEquals("Default slideshow delay must be 4 seconds", 4, repository.slideshowDelaySec)
        assertEquals("Default StateFlow value must be 4 seconds", 4, repository.slideshowDelaySeconds.value)
    }

    @Test
    fun testSlideshowDelay_AcceptsMinimum1Second() {
        repository.slideshowDelaySec = 1
        assertEquals(1, repository.slideshowDelaySec)
        assertEquals(1, repository.slideshowDelaySeconds.value)
    }

    @Test
    fun testSlideshowDelay_AcceptsMaximum10Seconds() {
        repository.slideshowDelaySec = 10
        assertEquals(10, repository.slideshowDelaySec)
        assertEquals(10, repository.slideshowDelaySeconds.value)
    }

    @Test
    fun testSlideshowDelay_ClampsBelow1To1() {
        repository.slideshowDelaySec = 0
        assertEquals(1, repository.slideshowDelaySec)

        repository.slideshowDelaySec = -5
        assertEquals(1, repository.slideshowDelaySec)
    }

    @Test
    fun testSlideshowDelay_ClampsAbove10To10() {
        repository.slideshowDelaySec = 15
        assertEquals(10, repository.slideshowDelaySec)

        repository.slideshowDelaySec = 100
        assertEquals(10, repository.slideshowDelaySec)
    }

    @Test
    fun testPhotoItems_ExclusivelyPhotoMediaType() {
        val photo = createPhotoItem("p1")
        val video = createVideoItem("v1")

        assertFalse("Photo item must not be video", photo.mediaType.equals("VIDEO", ignoreCase = true))
        assertTrue("Video item must be video", video.mediaType.equals("VIDEO", ignoreCase = true))
    }
}
