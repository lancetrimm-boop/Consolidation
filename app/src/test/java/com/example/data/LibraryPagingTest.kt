package com.example.data

import com.example.data.semantic.SearchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPagingTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createDummyItems(count: Int, prefix: String = "item"): List<MediaItem> {
        return (1..count).map { i ->
            MediaItem(
                id = "$prefix-$i",
                title = "Test Item $i",
                mediaType = if (i % 2 == 0) "VIDEO" else "PHOTO",
                year = 2024,
                duration = "01:00",
                genre = "Test",
                compatibilityStatus = CompatibilityStatus.PLAYABLE
            )
        }
    }

    @Test
    fun testInitialLoad_Returns25Items() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        val presentation = repository.latestLibraryPresentation.value
        assertEquals(25, presentation.mediaItems.size)
        assertEquals("item-1", presentation.mediaItems.first().id)
        assertEquals("item-25", presentation.mediaItems.last().id)
    }

    @Test
    fun testSecondPage_AppendsNext25Items_Produces50() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        assertEquals(50, presentation.mediaItems.size)
        assertEquals("item-1", presentation.mediaItems.first().id)
        assertEquals("item-50", presentation.mediaItems.last().id)
    }

    @Test
    fun testPaging_NoDuplicateIDs() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        val ids = presentation.mediaItems.map { it.id }
        assertEquals("All IDs in paged list must be unique", ids.toSet().size, ids.size)
    }

    @Test
    fun testRepeatedTrigger_DoesNotExceedTotalAvailable() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(30)
        repository.setMediaItemsForTesting(dummy)

        repository.loadNextLibraryPage()
        repository.loadNextLibraryPage()
        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        assertEquals(30, presentation.mediaItems.size)
    }

    @Test
    fun testSearchFilterSortState_SurvivesPaging() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        repository.libraryFilter = "VIDEO"
        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        assertEquals("VIDEO", presentation.selectedFilter)
        assertTrue(presentation.mediaItems.all { it.mediaType == "VIDEO" })
    }

    @Test
    fun testVisualSearch_RemainsVisualSearchAcrossPaging() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        repository.addVisualReference(dummy[0])

        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        assertTrue(presentation.activeVisualReferences.isNotEmpty())
        assertEquals(dummy[0].id, presentation.activeVisualReferences.first().id)
    }

    @Test
    fun testMultiVisualSearch_RemainsMultiVisualAcrossPaging() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        repository.addVisualReference(dummy[0])
        repository.addVisualReference(dummy[1])

        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        assertEquals(2, presentation.activeVisualReferences.size)
    }

    @Test
    fun testCompoundSearch_RemainsCompoundSearchAcrossPaging() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(60)
        repository.setMediaItemsForTesting(dummy)

        repository.addVisualReference(dummy[0])
        repository.librarySearchQuery = "test"

        repository.loadNextLibraryPage()

        val presentation = repository.latestLibraryPresentation.value
        assertTrue(presentation.searchRequest is SearchRequest.Compound)
        assertEquals("test", presentation.searchRequest.query)
    }

    @Test
    fun testGenuineExhaustion_StopsFurtherRequests() = runTest {
        backgroundScope.launch { repository.latestLibraryPresentation.collect {} }
        val dummy = createDummyItems(20)
        repository.setMediaItemsForTesting(dummy)

        val initialPresentation = repository.latestLibraryPresentation.value
        assertEquals(20, initialPresentation.mediaItems.size)

        repository.loadNextLibraryPage()
        val afterPresentation = repository.latestLibraryPresentation.value
        assertEquals(20, afterPresentation.mediaItems.size)
    }
}
