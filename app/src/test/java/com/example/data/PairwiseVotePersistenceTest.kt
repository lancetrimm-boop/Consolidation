package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class PairwiseVotePersistenceTest {

    private lateinit var repository: MediaRepository
    private lateinit var database: AuraDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository(testDispatcher)
        repository.setDatabaseForTesting(database)
    }

    private fun createMediaItem(id: String): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            mediaType = "PHOTO",
            year = 2024,
            duration = "",
            genre = "Media",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            rating = 5f,
            eloRating = 1500.0
        )
    }

    @Test
    fun testVotePersistenceAndSequencing() = runTest(testDispatcher) {
        val items = listOf("A", "B", "C", "D", "E").map { createMediaItem(it) }
        repository.setMediaItemsForTesting(items)
        items.forEach { database.mediaDao().insert(MediaEntity(id = it.id, title = it.title, mediaType = "PHOTO", compatibilityStatus = "PLAYABLE", rating = 5f, eloRating = 1500.0)) }
        
        repository.startCompareSelectionSession(setOf("A", "B", "C", "D", "E"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        val initialPair = repository.pairwiseState.value
        if (initialPair.optionA.id.isEmpty()) return@runTest

        val chosenId = initialPair.optionA.id
        repository.recordComparisonVote(chosenId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // 3. Verify persistence
        val outcomes = database.pairwiseDao().getAllOutcomes().filter { it.isNotEmpty() }.first()
        assertEquals(1, outcomes.size)
        assertEquals(chosenId, outcomes[0].chosenId)
    }

    @Test
    fun testPersistenceFailure_DoesNotAdvanceAndDoesNotIncrementCount() = runTest(testDispatcher) {
        val items = (1..6).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(items)
        items.forEach { database.mediaDao().insert(MediaEntity(id = it.id, title = it.title, mediaType = "PHOTO", compatibilityStatus = "PLAYABLE", rating = 5f, eloRating = 1500.0)) }
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4", "5", "6"))
        testDispatcher.scheduler.advanceUntilIdle()

        val initialPair = repository.pairwiseState.value
        val initialRound = repository.compareSelectionSession.value.roundNumber
        assertEquals(1, initialRound)

        // Close database to simulate persistence failure
        database.close()

        // Attempt to vote
        repository.recordComparisonVote(initialPair.optionA.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val counts = repository.getComparisonCounts()
        assertEquals(0, counts[initialPair.optionA.id] ?: 0)
        assertEquals(0, counts[initialPair.optionB.id] ?: 0)
        assertEquals(1, repository.compareSelectionSession.value.roundNumber)
    }

    @Test
    fun testEloRatingUpdateAndPersistence() = runTest(testDispatcher) {
        val items = listOf("A", "B", "C", "D", "E").map { createMediaItem(it) }
        repository.setMediaItemsForTesting(items)
        items.forEach { database.mediaDao().insert(MediaEntity(id = it.id, title = it.title, mediaType = "PHOTO", compatibilityStatus = "PLAYABLE", rating = 5f, eloRating = 1500.0)) }
        
        repository.startCompareSelectionSession(setOf("A", "B", "C", "D", "E"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        val initialPair = repository.pairwiseState.value
        val idA = initialPair.optionA.id
        val idB = initialPair.optionB.id
        if (idA.isEmpty() || idB.isEmpty()) return@runTest

        val chosenId = idA

        val initialEntityA = database.mediaDao().getMediaById(idA)
        val initialEntityB = database.mediaDao().getMediaById(idB)
        assertNotNull(initialEntityA)
        assertNotNull(initialEntityB)
        assertEquals(1500.0, initialEntityA!!.eloRating, 0.01)
        assertEquals(1500.0, initialEntityB!!.eloRating, 0.01)

        repository.recordComparisonVote(chosenId)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedEntityA = database.mediaDao().getMediaById(idA)
        val updatedEntityB = database.mediaDao().getMediaById(idB)
        assertNotNull(updatedEntityA)
        assertNotNull(updatedEntityB)

        assertTrue(updatedEntityA!!.eloRating > 1500.0)
        assertTrue(updatedEntityB!!.eloRating < 1500.0)
    }
}
