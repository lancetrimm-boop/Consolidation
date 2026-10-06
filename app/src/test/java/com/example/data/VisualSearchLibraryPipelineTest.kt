package com.example.data

import com.example.data.intelligence.*
import com.example.data.semantic.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class VisualSearchLibraryPipelineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MediaRepository
    private val hybridSearchEngine: HybridSearchEngine = mock()
    private val intelligenceCore: AuraIntelligenceCore = mock()

    private val item1 = MediaItem(id = "item1", title = "Mountain Sunset", mediaType = "PHOTO", uriPath = "path/1")
    private val item2 = MediaItem(id = "item2", title = "Ocean Waves", mediaType = "PHOTO", uriPath = "path/2")
    private val item3 = MediaItem(id = "item3", title = "City Lights", mediaType = "VIDEO", uriPath = "path/3")

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
        repository.setMediaItemsForTesting(listOf(item1, item2, item3))

        val engineField = MediaRepository::class.java.getDeclaredField("hybridSearchEngine")
        engineField.isAccessible = true
        engineField.set(repository, hybridSearchEngine)

        val coreField = MediaRepository::class.java.getDeclaredField("intelligenceCore")
        coreField.isAccessible = true
        coreField.set(repository, intelligenceCore)

        whenever(hybridSearchEngine.isSemanticReady()).thenReturn(true)
    }

    @After
    fun tearDown() {
        repository.close()
    }

    @Test
    fun testLoadLibraryPage_ActiveVisualSearch_ConsumesSearchRequest() = runTest(testDispatcher) {
        val vector = FloatArray(512) { 0.1f }
        val searchRequest = SearchRequest.Visual(vector, "path/1")

        val searchResult = HybridSearchResult(
            query = "Visual Query",
            queryType = SearchQueryType.VISUAL,
            requestId = "req1",
            candidates = listOf(
                HybridCandidate(
                    mediaId = "item2",
                    rrfScore = 0.95,
                    channelRanks = emptyMap(),
                    channelScores = emptyMap()
                )
            ),
            latencyMs = 10L,
            totalCandidatesConsidered = 1,
            channelCandidateCounts = emptyMap(),
            isSuccess = true
        )

        whenever(hybridSearchEngine.search(eq(searchRequest), any())).thenReturn(searchResult)

        // Act: loadLibraryPage with active SearchRequest.Visual
        val pageResults = repository.loadLibraryPage(
            searchRequest = searchRequest
        )

        // Assert: loadLibraryPage consumed the search request and returned search results instead of ordinary library
        assertEquals(1, pageResults.size)
        assertEquals("item2", pageResults[0].id)
        verify(hybridSearchEngine).search(eq(searchRequest), any())
    }

    @Test
    fun testLoadLibraryPage_MultipleVisualReferences_ReachesSearchPath() = runTest(testDispatcher) {
        val vectors = listOf(FloatArray(512) { 0.1f }, FloatArray(512) { 0.2f })
        val uris = listOf("path/1", "path/2")
        val multiRequest = SearchRequest.MultiVisual(vectors, uris)

        val searchResult = HybridSearchResult(
            query = "Visual Query",
            queryType = SearchQueryType.MULTI_VISUAL,
            requestId = "req2",
            candidates = listOf(
                HybridCandidate(
                    mediaId = "item3",
                    rrfScore = 0.88,
                    channelRanks = emptyMap(),
                    channelScores = emptyMap()
                )
            ),
            latencyMs = 10L,
            totalCandidatesConsidered = 1,
            channelCandidateCounts = emptyMap(),
            isSuccess = true
        )

        whenever(hybridSearchEngine.search(eq(multiRequest), any())).thenReturn(searchResult)

        // Act
        val pageResults = repository.loadLibraryPage(
            searchRequest = multiRequest
        )

        // Assert
        assertEquals(1, pageResults.size)
        assertEquals("item3", pageResults[0].id)
        verify(hybridSearchEngine).search(eq(multiRequest), any())
    }

    @Test
    fun testLoadLibraryPage_OrdinaryLibrary_NoActiveSearchRequest_Preserved() = runTest(testDispatcher) {
        val emptyTextRequest = SearchRequest.Text("")

        val mockResponse = IntelligenceResponse(
            requestId = "sort1",
            mode = IntelligenceMode.SORT,
            candidates = listOf(
                IntelligenceCandidate(item = item3, evidence = emptyList(), rankScore = 0.9, primaryRelevanceScore = 0.9f, secondaryEvidenceScore = 0.0f),
                IntelligenceCandidate(item = item1, evidence = emptyList(), rankScore = 0.8, primaryRelevanceScore = 0.8f, secondaryEvidenceScore = 0.0f)
            ),
            latencyMs = 5L,
            isSuccess = true
        )
        whenever(intelligenceCore.processRequest(any())).thenReturn(mockResponse)

        // Act: loadLibraryPage with empty search request
        val pageResults = repository.loadLibraryPage(
            searchRequest = emptyTextRequest
        )

        // Assert: Ordinary library sorting/filtering is returned, hybrid search engine is not invoked
        verify(hybridSearchEngine, never()).search(any<SearchRequest>(), any())
        assertEquals(2, pageResults.size)
        assertEquals("item3", pageResults[0].id)
        assertEquals("item1", pageResults[1].id)
    }

    @Test
    fun testSeeSimilar_BehaviorRemainsIntact() = runTest(testDispatcher) {
        val targetItem = item1
        val mockResponse = IntelligenceResponse(
            requestId = "sim1",
            mode = IntelligenceMode.SIMILAR,
            candidates = listOf(
                IntelligenceCandidate(item = item2, evidence = emptyList(), rankScore = 0.92, primaryRelevanceScore = 0.92f, secondaryEvidenceScore = 0.0f)
            ),
            latencyMs = 12L,
            isSuccess = true
        )

        whenever(intelligenceCore.processRequest(check {
            assertEquals(IntelligenceMode.SIMILAR, it.mode)
            assertEquals(targetItem.id, it.referenceItemId)
        })).thenReturn(mockResponse)

        // Act
        val similarResponse = repository.getSimilarMedia(targetItem, "test_request_1")

        // Assert
        assertTrue(similarResponse.isSuccess)
        assertEquals(1, similarResponse.candidates.size)
        assertEquals("item2", similarResponse.candidates[0].item.id)
    }
}
