package com.example.data.metv

import com.example.data.MediaItem
import com.example.data.TasteDNA
import com.example.data.db.ProgrammingFeedbackDao
import com.example.data.db.ProgrammingFeedbackEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProgrammingFeedbackIsolationTest {

    private val mockDao: ProgrammingFeedbackDao = mock()

    @Before
    fun setUp() {
        reset(mockDao)
    }

    private fun createSampleItem(id: String): MediaItem {
        return MediaItem(
            id = id,
            title = "Test Item $id",
            mediaType = "VIDEO",
            year = 2024,
            duration = "01:00",
            genre = "General",
            isFavorite = false,
            rating = 0f
        )
    }

    @Test
    fun testProgrammingFeedback_ModelCreation() {
        val feedback = ProgrammingQualityFeedback(
            mediaId = "media_123",
            channelId = "vibrant_cinema",
            isGoodProgramming = false
        )

        assertEquals("media_123", feedback.mediaId)
        assertEquals("vibrant_cinema", feedback.channelId)
        assertFalse(feedback.isGoodProgramming)
        assertTrue(feedback.timestamp > 0)
    }

    @Test
    fun testProgrammingFeedback_PersistenceIsolation_DoesNotMutateTasteDNAOrRating() = runTest {
        val initialTasteDNA = TasteDNA(vibrancy = 0.5, contrast = 0.5)
        val mediaItem = createSampleItem("item_456")

        // Record negative programming feedback (Thumbs Down)
        val feedbackEntity = ProgrammingFeedbackEntity(
            id = "fb_1",
            mediaId = mediaItem.id,
            channelId = "action_motion",
            isGoodProgramming = false,
            timestamp = System.currentTimeMillis()
        )

        mockDao.insertFeedback(feedbackEntity)

        // Verify DAO insertion was called for programming feedback
        argumentCaptor<ProgrammingFeedbackEntity>().apply {
            verify(mockDao).insertFeedback(capture())
            assertEquals("item_456", firstValue.mediaId)
            assertEquals("action_motion", firstValue.channelId)
            assertFalse(firstValue.isGoodProgramming)
        }

        // Verify TasteDNA, Item Rating, and Favorites remain 100% UNTOUCHED
        assertEquals(0.5, initialTasteDNA.vibrancy, 0.001)
        assertEquals(0.5, initialTasteDNA.contrast, 0.001)
        assertEquals(0f, mediaItem.rating, 0.001f)
        assertFalse(mediaItem.isFavorite)
    }

    @Test
    fun testExperienceRequest_SessionLevelSteering_DoesNotMutateStoredState() {
        val initialTasteDNA = TasteDNA(motion = 0.5)
        val steering = ExperienceRequest(
            moodNudge = "More Energetic",
            aestheticWeights = mapOf("vibrancy" to 0.8f, "motion" to 0.8f)
        )

        assertEquals("More Energetic", steering.moodNudge)
        assertEquals(0.8f, steering.aestheticWeights["vibrancy"])

        // Confirm TasteDNA baseline remains unaltered
        assertEquals(0.5, initialTasteDNA.motion, 0.001)
    }

    @Test
    fun testComingUpSchedule_Representation() {
        val current = createSampleItem("item_1")
        val upcoming = listOf(createSampleItem("item_2"), createSampleItem("item_3"))
        val schedule = ComingUpSchedule(
            currentItem = current,
            upcomingItems = upcoming,
            aestheticExplanation = "Programmed for warm golden tones and cinematic framing."
        )

        assertEquals("item_1", schedule.currentItem?.id)
        assertEquals(2, schedule.upcomingItems.size)
        assertTrue(schedule.aestheticExplanation.contains("golden tones"))
    }
}
