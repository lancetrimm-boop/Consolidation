package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositorySavedLearningTest {

    private lateinit var repository: MediaRepository
    private lateinit var database: AuraDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        try {
            if (::repository.isInitialized) {
                repository.close()
            }
        } catch (_: Exception) {}
        if (::database.isInitialized) {
            database.close()
        }
    }

    private fun TestScope.setupTestEnvironment(): MediaRepository {
        val testDispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()

        val repo = MediaRepository(testDispatcher)
        repo.setDatabaseForTesting(database)
        repo.setApplicationContextForTesting(context)
        
        // Enable fine-tuning
        val dna = TasteDNA(isFineTuningEnabled = true)
        repo.updateTasteDNA(dna, true, "TestSetup")
        return repo
    }

    private suspend fun insertTestMedia(id: String, title: String) {
        val entity = MediaEntity(
            id = id,
            title = title,
            mediaType = "PHOTO",
            uriPath = "content://media/external/images/media/$id",
            dateAdded = System.currentTimeMillis(),
            moodTagsJson = "vibrant,warm"
        )
        database.mediaDao().insert(entity)
        val item = entity.toMediaItem()
        repository.setMediaItemsForTesting(repository.mediaItems.value + item)
    }

    @Test
    fun testSaveMedia_IdempotentTasteDNAUpdates() = runTest {
        repository = setupTestEnvironment()
        insertTestMedia("m1", "Sunset Photo")
        advanceUntilIdle()

        val initialVibrancy = repository.tasteDNA.value.learnedVibrancy

        // First save
        repository.saveMedia("m1")
        advanceUntilIdle()

        val firstSaveVibrancy = repository.tasteDNA.value.learnedVibrancy
        assertTrue("First save should update learned vibrancy", firstSaveVibrancy > initialVibrancy)

        // Second save (idempotency check)
        repository.saveMedia("m1")
        advanceUntilIdle()

        val secondSaveVibrancy = repository.tasteDNA.value.learnedVibrancy
        assertEquals("Second save should not duplicate TasteDNA adjustment", firstSaveVibrancy, secondSaveVibrancy, 0.0001)

        assertTrue(repository.isSaved("m1"))
    }

    @Test
    fun testUnsaveMedia_LeavesTasteDNAStable() = runTest {
        repository = setupTestEnvironment()
        insertTestMedia("m2", "Forest Photo")
        advanceUntilIdle()

        // Save
        repository.saveMedia("m2")
        advanceUntilIdle()
        assertTrue(repository.isSaved("m2"))
        val savedVibrancy = repository.tasteDNA.value.learnedVibrancy

        // Unsave
        repository.removeMedia("m2")
        advanceUntilIdle()
        assertFalse(repository.isSaved("m2"))
        val unsavedVibrancy = repository.tasteDNA.value.learnedVibrancy

        assertEquals("Unsaving should not alter TasteDNA weights (avoiding non-linear drift)", savedVibrancy, unsavedVibrancy, 0.0001)
        assertFalse(repository.isSaved("m2"))
    }

    @Test
    fun testToggleSaved_ConcurrentReentrancySafety() = runTest {
        repository = setupTestEnvironment()
        insertTestMedia("m3", "Concurrent Photo")
        advanceUntilIdle()

        // Rapid toggles should not deadlock or throw
        repository.toggleSaved("m3")
        repository.toggleSaved("m3")
        repository.toggleSaved("m3")
        advanceUntilIdle()

        assertTrue(repository.isSaved("m3"))
    }
}
