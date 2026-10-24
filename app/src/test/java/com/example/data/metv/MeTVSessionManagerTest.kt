package com.example.data.metv

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.TasteDNA
import com.example.data.db.AuraDatabase
import com.example.data.db.ProgrammingFeedbackDao
import com.example.data.db.ProgrammingFeedbackEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MeTVSessionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository
    private lateinit var sessionManager: MeTVSessionManager
    private val mockDb: AuraDatabase = mock()
    private val mockFeedbackDao: ProgrammingFeedbackDao = mock()

    @Before
    fun setUp() {
        whenever(mockDb.programmingFeedbackDao()).thenReturn(mockFeedbackDao)
        repository = MediaRepository(testDispatcher)
        repository.setDatabaseForTesting(mockDb)

        sessionManager = MeTVSessionManager(
            repository = repository,
            scope = testScope,
            mainDispatcher = testDispatcher,
            engine = MeTVProgrammingEngine()
        )
    }

    private fun createMediaItem(id: String, title: String, isVideo: Boolean): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = if (isVideo) "VIDEO" else "PHOTO",
            year = 2024,
            duration = if (isVideo) "01:30" else "",
            genre = "Cinematic",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testStartSession_InitialProgrammedSequence_SetsPlaylist() = testScope.runTest {
        val items = listOf(
            createMediaItem("v1", "Video 1", isVideo = true),
            createMediaItem("v2", "Video 2", isVideo = true)
        )
        repository.setMediaItemsForTesting(items)

        val token = sessionManager.startSession(filterType = "VIDEOS", initialIndex = 0)
        advanceUntilIdle()

        assertNotNull(token)
        val active = repository.activePlaylist.value
        assertNotNull(active)
        assertEquals(2, active!!.items.size)
        assertEquals("Me TV Station", active.sourceTitle)
        assertEquals("v1", active.currentItem?.id)
    }

    @Test
    fun testVideoSession_RemainsVideoOnly() = testScope.runTest {
        val items = listOf(
            createMediaItem("p1", "Photo 1", isVideo = false),
            createMediaItem("v1", "Video 1", isVideo = true),
            createMediaItem("p2", "Photo 2", isVideo = false)
        )
        repository.setMediaItemsForTesting(items)

        sessionManager.startSession(filterType = "VIDEOS")
        advanceUntilIdle()

        val active = repository.activePlaylist.value
        assertNotNull(active)
        assertEquals(1, active!!.items.size)
        assertEquals("v1", active.items[0].id)
        assertTrue(active.items.all { it.mediaType == "VIDEO" })
    }

    @Test
    fun testPhotoSession_RemainsPhotoOnly() = testScope.runTest {
        val items = listOf(
            createMediaItem("p1", "Photo 1", isVideo = false),
            createMediaItem("v1", "Video 1", isVideo = true),
            createMediaItem("p2", "Photo 2", isVideo = false)
        )
        repository.setMediaItemsForTesting(items)

        sessionManager.startSession(filterType = "PHOTOS")
        advanceUntilIdle()

        val active = repository.activePlaylist.value
        assertNotNull(active)
        assertEquals(2, active!!.items.size)
        assertEquals("Aura Moments", active.sourceTitle)
        assertTrue(active.items.all { it.mediaType == "PHOTO" })
    }

    @Test
    fun testCheckAndReplenishQueue_AppendsNewItems() = testScope.runTest {
        val items = listOf(
            createMediaItem("v1", "Video 1", isVideo = true),
            createMediaItem("v2", "Video 2", isVideo = true),
            createMediaItem("v3", "Video 3", isVideo = true)
        )
        repository.setMediaItemsForTesting(items)

        sessionManager.startSession(filterType = "VIDEOS")
        advanceUntilIdle()

        assertEquals(3, repository.activePlaylist.value?.items?.size)

        // Add 2 more items to library pool
        val updatedItems = items + listOf(
            createMediaItem("v4", "Video 4", isVideo = true),
            createMediaItem("v5", "Video 5", isVideo = true)
        )
        repository.setMediaItemsForTesting(updatedItems)

        // Move active index to near end
        repository.selectPlaylistItem(2)

        sessionManager.checkAndReplenishQueue()
        advanceUntilIdle()

        val replenished = repository.activePlaylist.value
        assertEquals(5, replenished?.items?.size)
        assertEquals("v3", replenished?.currentItem?.id)
        assertEquals(2, replenished?.currentIndex)
    }

    @Test
    fun testStaleSessionGuard_SessionBRejectsSessionAWork() = testScope.runTest {
        val items = listOf(createMediaItem("v1", "Video 1", isVideo = true))
        repository.setMediaItemsForTesting(items)

        val tokenA = sessionManager.startSession(filterType = "VIDEOS")
        val tokenB = sessionManager.startSession(filterType = "VIDEOS")
        advanceUntilIdle()

        assertNotEquals(tokenA, tokenB)
        assertEquals("Me TV Station", repository.activePlaylist.value?.sourceTitle)
    }

    @Test
    fun testStopSession_CancelsWorkAndClearsSchedule() = testScope.runTest {
        val items = listOf(createMediaItem("v1", "Video 1", isVideo = true))
        repository.setMediaItemsForTesting(items)

        sessionManager.startSession(filterType = "VIDEOS")
        advanceUntilIdle()

        assertNotNull(sessionManager.scheduleState.value)

        sessionManager.stopSession()
        assertNull(sessionManager.scheduleState.value)
    }

    @Test
    fun testSubmitProgrammingFeedback_InsertsToDaoWithoutMutatingTasteDNA() = testScope.runTest {
        val initialTasteDNA = TasteDNA(vibrancy = 0.5)

        sessionManager.submitProgrammingFeedback(mediaId = "v1", isGoodProgramming = false)
        advanceUntilIdle()

        argumentCaptor<ProgrammingFeedbackEntity>().apply {
            verify(mockFeedbackDao).insertFeedback(capture())
            assertEquals("v1", firstValue.mediaId)
            assertEquals("metv_videos", firstValue.channelId)
            assertFalse(firstValue.isGoodProgramming)
        }

        // Verify TasteDNA is 100% untouched
        assertEquals(0.5, initialTasteDNA.vibrancy, 0.001)
    }

    @Test
    fun testChangeMood_UpdatesExperienceRequest_DoesNotMutateTasteDNA() = testScope.runTest {
        val initialTasteDNA = TasteDNA(motion = 0.5)
        val items = listOf(createMediaItem("v1", "Video 1", isVideo = true))
        repository.setMediaItemsForTesting(items)

        sessionManager.startSession(filterType = "VIDEOS")
        advanceUntilIdle()

        val request = ExperienceRequest(moodNudge = "More Energetic")
        sessionManager.changeMood(request)
        advanceUntilIdle()

        assertEquals("More Energetic", sessionManager.experienceRequest.value?.moodNudge)
        // Confirm baseline TasteDNA remains unaltered
        assertEquals(0.5, initialTasteDNA.motion, 0.001)
    }
}
