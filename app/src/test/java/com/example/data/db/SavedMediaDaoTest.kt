package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SavedMediaDaoTest {

    private lateinit var database: AuraDatabase
    private lateinit var savedMediaDao: SavedMediaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedMediaDao = database.savedMediaDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSaveAndRetrieveSingleItem() = runTest {
        val item = SavedMediaEntity(mediaId = "m1", savedAt = 1000L)
        savedMediaDao.saveMedia(item)

        assertTrue(savedMediaDao.isSaved("m1"))
        assertEquals(1, savedMediaDao.getSavedCount())

        val allSaved = savedMediaDao.getAllSavedSync()
        assertEquals(1, allSaved.size)
        assertEquals("m1", allSaved[0].mediaId)
        assertEquals(1000L, allSaved[0].savedAt)
    }

    @Test
    fun testSaveDuplicateMediaId_ReplacesOrUpdates() = runTest {
        val item1 = SavedMediaEntity(mediaId = "m1", savedAt = 1000L)
        val item2 = SavedMediaEntity(mediaId = "m1", savedAt = 2000L)
        
        savedMediaDao.saveMedia(item1)
        savedMediaDao.saveMedia(item2)

        assertEquals(1, savedMediaDao.getSavedCount())
        val allSaved = savedMediaDao.getAllSavedSync()
        assertEquals(1, allSaved.size)
        assertEquals("m1", allSaved[0].mediaId)
        assertEquals(2000L, allSaved[0].savedAt)
    }

    @Test
    fun testDeleteExistingAndNonExistentItem() = runTest {
        val item = SavedMediaEntity(mediaId = "m1", savedAt = 1000L)
        savedMediaDao.saveMedia(item)
        assertTrue(savedMediaDao.isSaved("m1"))

        // Delete existing
        savedMediaDao.deleteMedia("m1")
        assertFalse(savedMediaDao.isSaved("m1"))
        assertEquals(0, savedMediaDao.getSavedCount())

        // Delete non-existent (should be safe)
        savedMediaDao.deleteMedia("non-existent")
        assertEquals(0, savedMediaDao.getSavedCount())
    }

    @Test
    fun testOrderedNewestFirst() = runTest {
        savedMediaDao.saveMedia(SavedMediaEntity(mediaId = "m1", savedAt = 1000L))
        savedMediaDao.saveMedia(SavedMediaEntity(mediaId = "m2", savedAt = 3000L))
        savedMediaDao.saveMedia(SavedMediaEntity(mediaId = "m3", savedAt = 2000L))

        val allSaved = savedMediaDao.getAllSavedSync()
        assertEquals(3, allSaved.size)
        assertEquals("m2", allSaved[0].mediaId) // 3000
        assertEquals("m3", allSaved[1].mediaId) // 2000
        assertEquals("m1", allSaved[2].mediaId) // 1000
    }

    @Test
    fun testEmptyTableProducesEmptyResult() = runTest {
        assertFalse(savedMediaDao.isSaved("any"))
        assertEquals(0, savedMediaDao.getSavedCount())
        assertTrue(savedMediaDao.getAllSavedSync().isEmpty())
    }

    @Test
    fun testFlowObservations() = runTest {
        val flow = savedMediaDao.observeIsSaved("m1")
        assertFalse(flow.first())

        savedMediaDao.saveMedia(SavedMediaEntity(mediaId = "m1", savedAt = 1000L))
        assertTrue(savedMediaDao.observeIsSaved("m1").first())

        val listFlow = savedMediaDao.getAllSaved()
        assertEquals(1, listFlow.first().size)

        savedMediaDao.deleteMedia("m1")
        assertFalse(savedMediaDao.observeIsSaved("m1").first())
        assertTrue(listFlow.first().isEmpty())
    }
}
