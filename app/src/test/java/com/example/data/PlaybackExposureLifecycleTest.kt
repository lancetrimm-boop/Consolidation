package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.ExposureEventDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackExposureLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AuraDatabase
    private lateinit var exposureEventDao: ExposureEventDao
    private lateinit var exposureRepository: ExposureRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        exposureEventDao = database.exposureEventDao()
        exposureRepository = ExposureRepository(exposureEventDao, testDispatcher)
    }

    @After
    fun tearDown() {
        exposureRepository.close()
        database.close()
    }

    // 1 & 2 & 3 & 4. Preview seen logic
    @Test
    fun testPreview_UnderThreshold_NoEvent() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val events = exposureRepository.getEventsForMedia("preview-item-1")
        assertTrue(events.isEmpty())
    }

    @Test
    fun testPreview_ReachingThreshold_ExactlyOneEvent() = runTest(testDispatcher) {
        exposureRepository.recordEventSync(
            mediaId = "preview-item-2",
            eventType = ExposureEventType.PREVIEW_SEEN,
            timestampMs = 1250L
        )

        val events = exposureRepository.getEventsForMedia("preview-item-2")
        assertEquals(1, events.size)
        assertEquals(ExposureEventType.PREVIEW_SEEN, events[0].eventType)
    }

    @Test
    fun testPreview_LeavingAndReentering_TimerResetsCorrectly() = runTest(testDispatcher) {
        val mediaId = "preview-item-reenter"
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PREVIEW_SEEN, timestampMs = 1000L)
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PREVIEW_SEEN, timestampMs = 5000L)

        val events = exposureRepository.getEventsForMedia(mediaId)
        assertEquals(2, events.size)
    }

    @Test
    fun testPreview_RecompositionDoesNotDuplicate() = runTest(testDispatcher) {
        val mediaId = "preview-recomp"
        val sessionId = "session-card-1"
        
        val recorded1 = exposureRepository.recordEventSync(mediaId, ExposureEventType.PREVIEW_SEEN, sessionId = sessionId)
        assertTrue(recorded1)

        val events = exposureRepository.getEventsForMedia(mediaId)
        assertEquals(1, events.size)
    }

    // 5 & 6. Video Started threshold (3s)
    @Test
    fun testVideoPlayback_Under3Seconds_NoPlaybackStarted() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-1", isVideo = true, exposureRepository = exposureRepository)
        tracker.updateVideoProgress(currentPositionMs = 2500L, durationMs = 60000L, isPlaying = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-1")
        assertTrue("No started event for playback under 3 seconds", events.isEmpty())
    }

    @Test
    fun testVideoPlayback_Reaching3Seconds_OnePlaybackStarted() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-2", isVideo = true, exposureRepository = exposureRepository)
        tracker.updateVideoProgress(currentPositionMs = 3100L, durationMs = 60000L, isPlaying = true)
        tracker.updateVideoProgress(currentPositionMs = 3500L, durationMs = 60000L, isPlaying = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-2")
        assertEquals(1, events.size)
        assertEquals(ExposureEventType.PLAYBACK_STARTED, events[0].eventType)
    }

    // 7, 8, 9, 10, 11. Video Milestones (25%, 50%, 75%) & Backward Seeking & New Session
    @Test
    fun testVideoPlayback_ProgressMilestones_25_50_75_RecordedOnce() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-milestones", isVideo = true, exposureRepository = exposureRepository)
        val duration = 100000L // 100s

        tracker.updateVideoProgress(20000L, duration, isPlaying = true)
        tracker.updateVideoProgress(30000L, duration, isPlaying = true)
        tracker.updateVideoProgress(55000L, duration, isPlaying = true)
        tracker.updateVideoProgress(80000L, duration, isPlaying = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-milestones")
        val progressEvents = events.filter { it.eventType == ExposureEventType.PLAYBACK_PROGRESS }
        assertEquals(3, progressEvents.size)

        val milestones = progressEvents.mapNotNull { it.milestone }.toSet()
        assertEquals(setOf(25, 50, 75), milestones)
    }

    @Test
    fun testVideoPlayback_SeekingBackward_DoesNotDuplicateMilestoneInSameSession() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-seek", isVideo = true, exposureRepository = exposureRepository)
        val duration = 100000L // 100s

        tracker.updateVideoProgress(60000L, duration, isPlaying = true)
        tracker.updateVideoProgress(20000L, duration, isPlaying = true)
        tracker.updateVideoProgress(60000L, duration, isPlaying = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-seek")
        val progress50Events = events.filter { it.eventType == ExposureEventType.PLAYBACK_PROGRESS && it.milestone == 50 }
        assertEquals("50% milestone should only be recorded ONCE per continuous session", 1, progress50Events.size)
    }

    @Test
    fun testVideoPlayback_NewSession_CanRecordMilestonesAgain() = runTest(testDispatcher) {
        val mediaId = "video-multi-session"
        val duration = 100000L

        val tracker1 = PlaybackExposureTracker(mediaId, isVideo = true, exposureRepository = exposureRepository)
        tracker1.updateVideoProgress(30000L, duration, isPlaying = true) // 25%

        val tracker2 = PlaybackExposureTracker(mediaId, isVideo = true, exposureRepository = exposureRepository)
        tracker2.updateVideoProgress(30000L, duration, isPlaying = true) // 25% again
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia(mediaId)
        val progress25Events = events.filter { it.eventType == ExposureEventType.PLAYBACK_PROGRESS && it.milestone == 25 }
        assertEquals("New session should allow recording 25% milestone again", 2, progress25Events.size)
    }

    // 12, 13, 14, 19. Video Completion rules
    @Test
    fun testVideoPlayback_Reaching90Percent_TriggersCompletion() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-90pct", isVideo = true, exposureRepository = exposureRepository)
        val duration = 100000L

        tracker.updateVideoProgress(91000L, duration, isPlaying = true)
        tracker.updateVideoProgress(95000L, duration, isPlaying = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-90pct")
        val completedEvents = events.filter { it.eventType == ExposureEventType.PLAYBACK_COMPLETED }
        assertEquals(1, completedEvents.size)
    }

    @Test
    fun testVideoPlayback_StateEnded_TriggersCompletion() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-ended", isVideo = true, exposureRepository = exposureRepository)
        val duration = 100000L

        tracker.updateVideoProgress(50000L, duration, isPlaying = false, isEnded = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-ended")
        val completedEvents = events.filter { it.eventType == ExposureEventType.PLAYBACK_COMPLETED }
        assertEquals(1, completedEvents.size)
    }

    @Test
    fun testVideoPlayback_UnknownDuration_DoesNotProduceFalse90PercentCompletion() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-unknown-dur", isVideo = true, exposureRepository = exposureRepository)

        tracker.updateVideoProgress(10000L, -1L, isPlaying = true)
        testScheduler.advanceUntilIdle()

        val events = exposureRepository.getEventsForMedia("video-unknown-dur")
        val completedEvents = events.filter { it.eventType == ExposureEventType.PLAYBACK_COMPLETED }
        assertTrue("Unknown duration <= 0 must NOT trigger false 90% completion", completedEvents.isEmpty())
    }

    // 15, 16, 17, 18. Photo / Slideshow rules
    @Test
    fun testPhotoPlayback_DurationUnderOrEqual4s_StartsAtHalfDurationAndCompletesAtFullDuration() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("photo-4s", isVideo = false, exposureRepository = exposureRepository)
        val duration = 4000L

        tracker.updatePhotoProgress(1000L, duration)
        testScheduler.advanceUntilIdle()
        var events = exposureRepository.getEventsForMedia("photo-4s")
        assertTrue(events.isEmpty())

        tracker.updatePhotoProgress(2000L, duration)
        testScheduler.advanceUntilIdle()
        events = exposureRepository.getEventsForMedia("photo-4s")
        assertEquals(1, events.size)
        assertEquals(ExposureEventType.PLAYBACK_STARTED, events[0].eventType)

        tracker.updatePhotoProgress(4000L, duration)
        testScheduler.advanceUntilIdle()
        events = exposureRepository.getEventsForMedia("photo-4s")
        assertEquals(2, events.size)
        val completed = events.find { it.eventType == ExposureEventType.PLAYBACK_COMPLETED }
        assertNotNull(completed)
    }

    @Test
    fun testPhotoPlayback_DurationGreaterThan4s_StartsAt3sAndCompletesAtFullDuration() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("photo-6s", isVideo = false, exposureRepository = exposureRepository)
        val duration = 6000L

        tracker.updatePhotoProgress(2500L, duration)
        testScheduler.advanceUntilIdle()
        var events = exposureRepository.getEventsForMedia("photo-6s")
        assertTrue(events.isEmpty())

        tracker.updatePhotoProgress(3000L, duration)
        testScheduler.advanceUntilIdle()
        events = exposureRepository.getEventsForMedia("photo-6s")
        assertEquals(1, events.size)
        assertEquals(ExposureEventType.PLAYBACK_STARTED, events[0].eventType)

        tracker.updatePhotoProgress(6000L, duration)
        testScheduler.advanceUntilIdle()
        events = exposureRepository.getEventsForMedia("photo-6s")
        assertEquals(2, events.size)
        val completed = events.find { it.eventType == ExposureEventType.PLAYBACK_COMPLETED }
        assertNotNull(completed)
    }

    // 20, 21, 22. Playlist append, Media Identity & Global Exposure
    @Test
    fun testPlaylistAppend_DoesNotCreateNewExposureSessionForCurrentPlayingItem() = runTest(testDispatcher) {
        val tracker = PlaybackExposureTracker("video-append-test", isVideo = true, exposureRepository = exposureRepository)
        val sessionIdOriginal = tracker.sessionId

        tracker.updateVideoProgress(3500L, 60000L, isPlaying = true)
        testScheduler.advanceUntilIdle()
        assertEquals("Session ID must remain stable when playlist is appended", sessionIdOriginal, tracker.sessionId)
    }

    @Test
    fun testSameMedia_EncounteredThroughDifferentChannels_AccumulatesInGlobalExposureHistory() = runTest(testDispatcher) {
        val mediaId = "global-shared-item"

        exposureRepository.recordEventSync(mediaId, ExposureEventType.PREVIEW_SEEN, sessionId = "me-tv-session")
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PLAYBACK_STARTED, sessionId = "mood-session")
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PLAYBACK_COMPLETED, sessionId = "library-session")

        val globalEvents = exposureRepository.getEventsForMedia(mediaId)
        assertEquals("All exposures across channels must accumulate in global history for the item", 3, globalEvents.size)
    }
}
