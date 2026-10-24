package com.example.data

import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.CreatorDao
import com.example.data.db.EvidenceDao
import com.example.data.db.MediaDao
import com.example.data.db.PairwiseDao
import com.example.data.db.SearchHistoryDao
import com.example.data.db.UserPreferenceDao
import com.example.data.db.UserPreferenceEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColdStartTasteDNALoadOrderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: AuraDatabase
    private lateinit var userPreferenceDao: UserPreferenceDao
    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        database = mock(AuraDatabase::class.java)
        userPreferenceDao = mock(UserPreferenceDao::class.java)

        val mediaDao = mock(MediaDao::class.java)
        `when`(mediaDao.getAllMedia()).thenReturn(flowOf(emptyList()))
        `when`(mediaDao.getWatchHistory()).thenReturn(flowOf(emptyList()))
        `when`(database.mediaDao()).thenReturn(mediaDao)

        val searchDao = mock(SearchHistoryDao::class.java)
        `when`(searchDao.getRecentSearches()).thenReturn(flowOf(emptyList()))
        `when`(database.searchHistoryDao()).thenReturn(searchDao)

        val pairwiseDao = mock(PairwiseDao::class.java)
        `when`(pairwiseDao.getAllOutcomes()).thenReturn(flowOf(emptyList()))
        `when`(database.pairwiseDao()).thenReturn(pairwiseDao)

        val creatorDao = mock(CreatorDao::class.java)
        `when`(creatorDao.getAllCreators()).thenReturn(flowOf(emptyList()))
        `when`(database.creatorDao()).thenReturn(creatorDao)

        val evidenceDao = mock(EvidenceDao::class.java)
        `when`(evidenceDao.getAllEvidence()).thenReturn(flowOf(emptyList()))
        `when`(database.evidenceDao()).thenReturn(evidenceDao)

        `when`(database.userPreferenceDao()).thenReturn(userPreferenceDao)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = MediaRepository(testDispatcher)
        repository.setApplicationContextForTesting(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testColdStart_PersistedTasteDNA_IsLoadedOnDatabaseInitialization() = testScope.runTest {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(TasteDNA::class.java)

        val customDNA = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.95,
            learnedVibrancy = 0.95,
            confVibrancy = 1.0,
        )
        val json = adapter.toJson(customDNA)

        `when`(userPreferenceDao.getPreference("taste_dna")).thenReturn(
            UserPreferenceEntity(
                key = "taste_dna",
                value = json,
            ),
        )

        // Run database observers
        repository.startDatabaseObservers(database)
        advanceUntilIdle()

        val loadedDNA = repository.tasteDNA.value

        // Verify persisted TasteDNA state is observed instead of neutral defaults
        assertTrue(loadedDNA.isFineTuningEnabled)
        assertEquals(0.95, loadedDNA.vibrancy, 0.001)
        assertEquals(0.95, loadedDNA.effectiveVibrancy, 0.001)
    }
}
