package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaRepositorySavedLearningTest {

    private lateinit var repository: MediaRepository
    private lateinit var database: AuraDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MediaRepository()
        repository.setApplicationContextForTesting(context)
        repository.setDatabaseForTesting(database)
        
        // Enable fine-tuning
        val dna = TasteDNA(isFineTuningEnabled = true)
        repository.updateTasteDNA(dna, true, "TestSetup")
    }

    @After
    fun tearDown() {
        repository.close()
        database.close()
    }

    private fun insertTestMedia(id: String, title: String) {
        val entity = MediaEntity(
            id = id,
            title = title,
            mediaType = "PHOTO",
            uriPath = "content://media/external/images/media/$id",
            dateAdded = System.currentTimeMillis(),
            moodTagsJson = "[\"vibrant\",\"warm\"]"
        )
        runBlocking { 
            database.mediaDao().insert(entity)
            val item = entity.toMediaItem()
            repository.setMediaItemsForTesting(repository.mediaItems.value + item)
        }
    }

    @Test
    fun testSaveMedia_IdempotentTasteDNAUpdates() = runBlocking {
        insertTestMedia("m1", "Sunset Photo")

        val initialVibrancy = repository.tasteDNA.value.learnedVibrancy

        // First save
        repository.saveMedia("m1")
        withTimeout(5000) {
            while (repository.tasteDNA.value.learnedVibrancy <= initialVibrancy) {
                delay(10)
            }
        }

        val firstSaveVibrancy = repository.tasteDNA.value.learnedVibrancy
        assertTrue("First save should update learned vibrancy", firstSaveVibrancy > initialVibrancy)

        // Second save (idempotency check)
        repository.saveMedia("m1")
        delay(100) // Small delay to allow any potential duplicate update to occur if non-idempotent

        val secondSaveVibrancy = repository.tasteDNA.value.learnedVibrancy
        assertEquals("Second save should not duplicate TasteDNA adjustment", firstSaveVibrancy, secondSaveVibrancy, 0.0001)

        assertTrue(repository.isSaved("m1"))
    }

    @Test
    fun testUnsaveMedia_LeavesTasteDNAStable() = runBlocking {
        insertTestMedia("m2", "Forest Photo")

        // Save
        repository.saveMedia("m2")
        withTimeout(5000) {
            while (!repository.isSaved("m2")) {
                delay(10)
            }
        }
        val savedVibrancy = repository.tasteDNA.value.learnedVibrancy

        // Unsave
        repository.removeMedia("m2")
        withTimeout(5000) {
            while (repository.isSaved("m2")) {
                delay(10)
            }
        }
        val unsavedVibrancy = repository.tasteDNA.value.learnedVibrancy

        assertEquals("Unsaving should not alter TasteDNA weights (avoiding non-linear drift)", savedVibrancy, unsavedVibrancy, 0.0001)
        assertFalse(repository.isSaved("m2"))
    }

    @Test
    fun testToggleSaved_ConcurrentReentrancySafety() = runBlocking {
        insertTestMedia("m3", "Concurrent Photo")

        // Rapid toggles should not deadlock or throw
        repository.toggleSaved("m3")
        repository.toggleSaved("m3")
        repository.toggleSaved("m3")
        withTimeout(5000) {
            while (!repository.isSaved("m3")) {
                delay(10)
            }
        }

        assertTrue(repository.isSaved("m3"))
    }
}
