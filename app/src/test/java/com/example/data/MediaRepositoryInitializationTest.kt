package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AIState
import com.example.data.db.AuraDatabase
import com.example.data.db.PassphraseManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryInitializationTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PassphraseManager.clearPassphrase(context)
        context.deleteDatabase("aura_intelligence.db")
        AuraDatabase.clearInstance()
        repository = MediaRepository(testDispatcher)
        repository.resetTestingState()
    }

    @Test
    fun testConstructor_InitializesBootstrapCore() {
        assertNotNull(repository.intelligenceCore)
        assertNotNull(repository.hybridSearchEngine)
        assertEquals(AIState.NOT_INITIALIZED, repository.aiState.value)
    }

    @Test
    fun testInitDatabase_AwaitsEarlyAIReady() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val job = launch { repository.initDatabase(context) }
        testScheduler.advanceUntilIdle()
        
        val dbState = repository.databaseState.value
        assertEquals(DatabaseState.READY, dbState)
        
        assertNotEquals(AIState.NOT_INITIALIZED, repository.aiState.value)
        assertNotNull(repository.intelligenceCore)
        job.cancel()
    }
    
    @Test
    fun testFlows_RecomputeOnAIStateChange() = runTest(testDispatcher) {
        val item1 = MediaItem("1", "Test", "PHOTO", 2024, "", "Genre", compatibilityStatus = CompatibilityStatus.PLAYABLE)
        repository.setMediaItemsForTesting(listOf(item1))
        
        val initialRecommendation = repository.latestAiSortRecommendation.value
        assertNotNull(initialRecommendation)
    }
}
