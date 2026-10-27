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
import kotlinx.coroutines.flow.first
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
class MediaRepositorySavedTest {

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
        return repo
    }

    private suspend fun setMediaItemsForTesting(items: List<MediaItem>) {
        val entities = items.map { item ->
            MediaEntity(
                id = item.id,
                title = item.title,
                mediaType = item.mediaType,
                year = item.year,
                duration = item.duration,
                genre = item.genre,
                compatibilityStatus = item.compatibilityStatus.name
            )
        }
        database.mediaDao().insertAll(entities)
        repository.setMediaItemsForTesting(items)
    }

    private fun createMediaItem(id: String, title: String): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "VIDEO",
            duration = "01:00",
            genre = "Test",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testSaveMedia_CreatesSavedStateAndTimestamp() = runTest {
        repository = setupTestEnvironment()
        val item = createMediaItem("m1", "Media 1")
        setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        assertFalse(repository.isSaved("m1"))

        repository.saveMedia("m1")
        advanceUntilIdle()

        assertTrue(repository.isSaved("m1"))
        val savedList = repository.observeSavedMedia().first()
        assertEquals(1, savedList.size)
        assertEquals("m1", savedList[0].id)
    }

    @Test
    fun testRemoveMedia_RemovesSavedState_SafeForUnsaved() = runTest {
        repository = setupTestEnvironment()
        val item = createMediaItem("m1", "Media 1")
        setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        repository.saveMedia("m1")
        advanceUntilIdle()
        assertTrue(repository.isSaved("m1"))

        // Remove saved item
        repository.removeMedia("m1")
        advanceUntilIdle()
        assertFalse(repository.isSaved("m1"))
        assertTrue(repository.observeSavedMedia().first().isEmpty())

        // Remove unsaved item (should be safe)
        repository.removeMedia("non-existent")
        advanceUntilIdle()
        assertFalse(repository.isSaved("non-existent"))
    }

    @Test
    fun testToggleSaved_AlternatesState() = runTest {
        repository = setupTestEnvironment()
        val item = createMediaItem("m1", "Media 1")
        setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        assertFalse(repository.isSaved("m1"))

        // Toggle -> Saved
        repository.toggleSaved("m1")
        advanceUntilIdle()
        assertTrue(repository.isSaved("m1"))

        // Toggle -> Unsaved
        repository.toggleSaved("m1")
        advanceUntilIdle()
        assertFalse(repository.isSaved("m1"))
    }

    @Test
    fun testReactiveStateAndOrdering_NewestFirst() = runTest {
        repository = setupTestEnvironment()
        val item1 = createMediaItem("m1", "Media 1")
        val item2 = createMediaItem("m2", "Media 2")
        val item3 = createMediaItem("m3", "Media 3")
        setMediaItemsForTesting(listOf(item1, item2, item3))
        advanceUntilIdle()

        val isSavedFlow = repository.observeIsSaved("m1")
        assertFalse(isSavedFlow.first())

        repository.saveMedia("m1")
        advanceUntilIdle()
        assertTrue(repository.observeIsSaved("m1").first())

        // Save m2 and m3
        repository.saveMedia("m2")
        advanceUntilIdle()
        repository.saveMedia("m3")
        advanceUntilIdle()

        val savedList = repository.observeSavedMedia().first()
        assertEquals(3, savedList.size)
        // Ordering should be newest first (m3, then m2, then m1)
        assertEquals("m3", savedList[0].id)
        assertEquals("m2", savedList[1].id)
        assertEquals("m1", savedList[2].id)
    }

    @Test
    fun testMissingMedia_ToleratesAndExcludesNonExistentItems() = runTest {
        repository = setupTestEnvironment()
        // Save an ID that has no corresponding MediaItem in the library
        repository.saveMedia("ghost_id")
        advanceUntilIdle()

        assertTrue(repository.isSaved("ghost_id"))

        // observeSavedMedia should not crash and should exclude ghost_id
        val savedList = repository.observeSavedMedia().first()
        assertTrue(savedList.isEmpty())
    }

    @Test
    fun testRecordMediaCompletion_RemovesFromSaved() = runTest {
        repository = setupTestEnvironment()
        val item = createMediaItem("m1", "Media 1")
        setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        repository.saveMedia("m1")
        advanceUntilIdle()
        assertTrue(repository.isSaved("m1"))

        // Record media completion
        repository.recordMediaCompletion("m1")
        advanceUntilIdle()

        assertFalse(repository.isSaved("m1"))
        assertTrue(repository.observeSavedMedia().first().isEmpty())
    }

    @Test
    fun testIncompletePlayback_PreservesSaved() = runTest {
        repository = setupTestEnvironment()
        val item = createMediaItem("m1", "Media 1")
        setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        repository.saveMedia("m1")
        advanceUntilIdle()
        assertTrue(repository.isSaved("m1"))

        // Incomplete playback (e.g. view count or normal view without completion)
        repository.recordView("m1")
        advanceUntilIdle()

        assertTrue(repository.isSaved("m1"))
        assertEquals(1, repository.observeSavedMedia().first().size)
    }

    @Test
    fun testRepeatedCompletion_IsIdempotent() = runTest {
        repository = setupTestEnvironment()
        val item = createMediaItem("m1", "Media 1")
        setMediaItemsForTesting(listOf(item))
        advanceUntilIdle()

        repository.saveMedia("m1")
        advanceUntilIdle()
        assertTrue(repository.isSaved("m1"))

        // Record completion multiple times
        repository.recordMediaCompletion("m1")
        repository.recordMediaCompletion("m1")
        advanceUntilIdle()

        assertFalse(repository.isSaved("m1"))
        assertTrue(repository.observeSavedMedia().first().isEmpty())
    }
}
