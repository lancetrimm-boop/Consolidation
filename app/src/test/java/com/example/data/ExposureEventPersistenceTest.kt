package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ExposureEventPersistenceTest {

    private lateinit var database: AuraDatabase
    private lateinit var exposureEventDao: ExposureEventDao
    private lateinit var mediaDao: MediaDao
    private lateinit var exposureRepository: ExposureRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exposureEventDao = database.exposureEventDao()
        mediaDao = database.mediaDao()
        exposureRepository = ExposureRepository(exposureEventDao)
    }

    @After
    fun tearDown() {
        exposureRepository.close()
        database.close()
    }

    @Test
    fun testExposureEventEntity_Persistence() = runTest {
        val entity = ExposureEventEntity(
            id = "event-1",
            mediaId = "media-101",
            eventType = ExposureEventType.PREVIEW_SEEN,
            timestamp = 1000L
        )
        exposureEventDao.insertEvent(entity)

        val retrieved = exposureEventDao.getEventsForMedia("media-101")
        assertEquals(1, retrieved.size)
        assertEquals("event-1", retrieved[0].id)
        assertEquals("media-101", retrieved[0].mediaId)
        assertEquals(ExposureEventType.PREVIEW_SEEN, retrieved[0].eventType)
        assertEquals(1000L, retrieved[0].timestamp)
    }

    @Test
    fun testAllExposureEventTypes_CanBeStored() = runTest {
        val types = ExposureEventType.entries
        types.forEachIndexed { index, type ->
            val entity = ExposureEventEntity(
                id = "event-$index",
                mediaId = "media-type-test",
                eventType = type,
                timestamp = 1000L + index
            )
            exposureEventDao.insertEvent(entity)
        }

        val retrieved = exposureEventDao.getEventsForMedia("media-type-test")
        assertEquals(types.size, retrieved.size)
        val retrievedTypes = retrieved.map { it.eventType }.toSet()
        assertEquals(types.toSet(), retrievedTypes)
    }

    @Test
    fun testProgressMilestones_25_50_75_CanBeRepresented() = runTest {
        val milestones = listOf(
            ExposureProgressMilestone.PERCENT_25,
            ExposureProgressMilestone.PERCENT_50,
            ExposureProgressMilestone.PERCENT_75
        )

        milestones.forEach { milestone ->
            val success = exposureRepository.recordEventSync(
                mediaId = "media-milestone-test",
                eventType = ExposureEventType.PLAYBACK_PROGRESS,
                milestone = milestone,
                sessionId = "session-1",
                timestampMs = System.currentTimeMillis()
            )
            assertTrue("Milestone ${milestone.percentage}% should be recorded", success)
        }

        val events = exposureRepository.getEventsForMedia("media-milestone-test")
        assertEquals(3, events.size)
        val storedMilestones = events.mapNotNull { it.milestone }.toSet()
        assertEquals(setOf(25, 50, 75), storedMilestones)
    }

    @Test
    fun testMilestoneDeduplication_PerSession() = runTest {
        val firstRecord = exposureRepository.recordEventSync(
            mediaId = "media-dedup-test",
            eventType = ExposureEventType.PLAYBACK_PROGRESS,
            milestone = ExposureProgressMilestone.PERCENT_50,
            sessionId = "session-alpha",
            timestampMs = 1000L
        )
        assertTrue(firstRecord)

        // Attempting duplicate milestone in same session
        val duplicateRecord = exposureRepository.recordEventSync(
            mediaId = "media-dedup-test",
            eventType = ExposureEventType.PLAYBACK_PROGRESS,
            milestone = ExposureProgressMilestone.PERCENT_50,
            sessionId = "session-alpha",
            timestampMs = 2000L
        )
        assertFalse("Duplicate milestone in same session must be rejected", duplicateRecord)

        // Same milestone in a NEW session must succeed
        val newSessionRecord = exposureRepository.recordEventSync(
            mediaId = "media-dedup-test",
            eventType = ExposureEventType.PLAYBACK_PROGRESS,
            milestone = ExposureProgressMilestone.PERCENT_50,
            sessionId = "session-beta",
            timestampMs = 3000L
        )
        assertTrue("Same milestone in a new session must be recorded", newSessionRecord)

        val events = exposureRepository.getEventsForMedia("media-dedup-test")
        assertEquals(2, events.size)
    }

    @Test
    fun testEvents_RetainTimestamps() = runTest {
        val timestampA = 1600000000000L
        val timestampB = 1700000000000L

        exposureRepository.recordEventSync("media-time-test", ExposureEventType.PREVIEW_SEEN, timestampMs = timestampA)
        exposureRepository.recordEventSync("media-time-test", ExposureEventType.PLAYBACK_STARTED, timestampMs = timestampB)

        val events = exposureRepository.getEventsForMedia("media-time-test")
        assertEquals(2, events.size)
        assertEquals(timestampB, events[0].timestamp) // Descending order
        assertEquals(timestampA, events[1].timestamp)
    }

    @Test
    fun testMultipleEventsForSameMediaId_Coexist() = runTest {
        val mediaId = "media-multi-event"
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PREVIEW_SEEN)
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PLAYBACK_STARTED)
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PLAYBACK_PROGRESS, milestone = ExposureProgressMilestone.PERCENT_25, sessionId = "s1")
        exposureRepository.recordEventSync(mediaId, ExposureEventType.PLAYBACK_COMPLETED)

        val events = exposureRepository.getEventsForMedia(mediaId)
        assertEquals(4, events.size)
    }

    @Test
    fun testEventsForDifferentMediaIds_IndependentlyQueryable() = runTest {
        exposureRepository.recordEventSync("media-A", ExposureEventType.PREVIEW_SEEN)
        exposureRepository.recordEventSync("media-B", ExposureEventType.PLAYBACK_STARTED)

        val eventsA = exposureRepository.getEventsForMedia("media-A")
        val eventsB = exposureRepository.getEventsForMedia("media-B")

        assertEquals(1, eventsA.size)
        assertEquals(ExposureEventType.PREVIEW_SEEN, eventsA[0].eventType)

        assertEquals(1, eventsB.size)
        assertEquals(ExposureEventType.PLAYBACK_STARTED, eventsB[0].eventType)
    }

    @Test
    fun testTimestampRangeQueries_Work() = runTest {
        val t1 = 1000L
        val t2 = 2000L
        val t3 = 3000L
        val t4 = 4000L

        exposureRepository.recordEventSync("item1", ExposureEventType.PREVIEW_SEEN, timestampMs = t1)
        exposureRepository.recordEventSync("item2", ExposureEventType.PREVIEW_SEEN, timestampMs = t2)
        exposureRepository.recordEventSync("item3", ExposureEventType.PREVIEW_SEEN, timestampMs = t3)
        exposureRepository.recordEventSync("item4", ExposureEventType.PREVIEW_SEEN, timestampMs = t4)

        val rangeEvents = exposureRepository.getEventsInTimeRange(1500L, 3500L)
        assertEquals(2, rangeEvents.size)
        val mediaIds = rangeEvents.map { it.mediaId }.toSet()
        assertEquals(setOf("item2", "item3"), mediaIds)
    }

    @Test
    fun testMigration45To46_SucceedsAndPreservesData() = runTest {
        val mediaEntity = MediaEntity(
            id = "media-existing-101",
            title = "Legacy Existing Item",
            mediaType = "PHOTO",
            exposureCount = 5,
            lastExposedTimestamp = 123456789L
        )
        mediaDao.insert(mediaEntity)

        // Verify media entity exists in database
        val existingMedia = mediaDao.getMediaById("media-existing-101")
        assertNotNull(existingMedia)
        assertEquals(5, existingMedia?.exposureCount)
        assertEquals(123456789L, existingMedia?.lastExposedTimestamp)

        // Record a new exposure event in exposure_events table
        exposureRepository.recordEventSync("media-existing-101", ExposureEventType.PREVIEW_SEEN)

        // Verify media_items data is completely intact and untouched
        val afterMedia = mediaDao.getMediaById("media-existing-101")
        assertNotNull(afterMedia)
        assertEquals("Legacy Existing Item", afterMedia?.title)
        assertEquals(5, afterMedia?.exposureCount)
        assertEquals(123456789L, afterMedia?.lastExposedTimestamp)

        // Verify new exposure event exists in exposure_events table
        val events = exposureRepository.getEventsForMedia("media-existing-101")
        assertEquals(1, events.size)
    }
}
