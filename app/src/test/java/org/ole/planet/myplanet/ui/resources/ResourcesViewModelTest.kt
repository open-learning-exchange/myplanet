package org.ole.planet.myplanet.ui.resources

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesViewModelTest {

    private lateinit var viewModel: ResourcesViewModel
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        viewModel = ResourcesViewModel(
            resourcesRepository,
            userRepository,
            dispatcherProvider
        )
    }

    @Test
    fun `currentUser is populated on init and getCurrentUser returns it`() = runTest {
        val mockUser = UserEntity().apply { id = "user123" }
        coEvery { userRepository.getUserModel() } returns mockUser

        viewModel = ResourcesViewModel(resourcesRepository, userRepository, dispatcherProvider)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockUser, viewModel.currentUser.value)
        assertEquals(mockUser, viewModel.getCurrentUser())
    }

    @Test
    fun `addResourcesToUserLibrary with successful result returns success`() = runTest {
        val resourceIds = listOf("res1", "res2")
        val userId = "user123"
        coEvery { resourcesRepository.addResourcesToUserLibrary(resourceIds, userId) } returns Result.success(Unit)

        val result = viewModel.addResourcesToUserLibrary(resourceIds, userId)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `addResourcesToUserLibrary with failure result returns failure`() = runTest {
        val resourceIds = listOf("res1", "res2")
        val userId = "user123"
        val exception = Exception("Failed to add resources")
        coEvery { resourcesRepository.addResourcesToUserLibrary(resourceIds, userId) } returns Result.failure(exception)

        val result = viewModel.addResourcesToUserLibrary(resourceIds, userId)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `addResourcesToUserLibrary with empty resource list returns result from repository`() = runTest {
        val resourceIds = emptyList<String>()
        val userId = "user123"
        coEvery { resourcesRepository.addResourcesToUserLibrary(resourceIds, userId) } returns Result.success(Unit)

        val result = viewModel.addResourcesToUserLibrary(resourceIds, userId)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `observeOpenedResourceIds updates openedResourceIds state flow`() = runTest {
        val userId = "user123"
        val mockFlow = kotlinx.coroutines.flow.flowOf(setOf("res1", "res2"))
        coEvery { resourcesRepository.observeOpenedResourceIds(userId) } returns mockFlow

        viewModel.observeOpenedResourceIds(userId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("res1", "res2"), viewModel.openedResourceIds.value)
    }

    @Test
    fun `loadResources asynchronously updates resourcesState`() = runTest {
        val mockLibrary = MyLibrary().apply {
            id = "lib1"
            title = "Library 1"
            resourceOffline = true
        }
        val mockTag = TagEntity().apply {
            id = "tag1"
            name = "Tag 1"
        }
        val mockResourceItem = mockk<ResourceItem>(relaxed = true)
        coEvery { resourcesRepository.getResourceListModels(any(), any()) } returns listOf(
            ResourceListModel(mockLibrary, mockResourceItem, listOf(TagItem(mockTag.id, mockTag.name)))
        )

        viewModel.loadResources(true, "modelId")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.resourcesState.value.size)
        assertEquals("lib1", viewModel.resourcesState.value[0].library.id)
    }

    @Test
    fun `getCachedResources returns cached items from repository`() = runTest {
        val mockLibrary = MyLibrary().apply {
            id = "lib1"
            title = "Library 1"
        }
        val mockResourceItem = mockk<ResourceItem>(relaxed = true)
        val cached = listOf(
            ResourceListModel(mockLibrary, mockResourceItem, emptyList())
        )
        every { resourcesRepository.getCachedResourceListModels(true, "modelId") } returns cached

        val result = viewModel.getCachedResources(true, "modelId")
        assertEquals(1, result?.size)
        assertEquals("lib1", result?.get(0)?.library?.id)
    }

    @Test
    fun `toggleTitleSortOrder sorts A to Z on first toggle and Z to A on second toggle`() = runTest {
        val itemB = createResourceModel("Banana", 100L)
        val itemA = createResourceModel("Apple", 200L)
        val itemC = createResourceModel("Cherry", 300L)
        val list = listOf(itemB, itemA, itemC)

        val firstToggleResult = viewModel.toggleTitleSortOrder(list)
        assertEquals(listOf("Apple", "Banana", "Cherry"), firstToggleResult.map { it.item.title })

        val secondToggleResult = viewModel.toggleTitleSortOrder(firstToggleResult)
        assertEquals(listOf("Cherry", "Banana", "Apple"), secondToggleResult.map { it.item.title })
    }

    @Test
    fun `toggleTitleSortOrder keeps input order for tied titles when ascending`() = runTest {
        val item1 = createResourceModel("apple")
        val item2 = createResourceModel("Apple")
        val list = listOf(item1, item2)

        val result = viewModel.toggleTitleSortOrder(list)
        assertEquals(listOf("apple", "Apple"), result.map { it.item.title })
        assertEquals(listOf(item1, item2), result)
    }

    @Test
    fun `toggleTitleSortOrder keeps input order for tied titles when descending`() = runTest {
        val item1 = createResourceModel("apple")
        val item2 = createResourceModel("Apple")
        val list = listOf(item1, item2)

        val firstToggleResult = viewModel.toggleTitleSortOrder(list)
        val secondToggleResult = viewModel.toggleTitleSortOrder(firstToggleResult)
        assertEquals(listOf("apple", "Apple"), secondToggleResult.map { it.item.title })
        assertEquals(listOf(item1, item2), secondToggleResult)
    }

    @Test
    fun `toggleTitleSortOrder handles null title as empty string`() = runTest {
        val itemNull = createResourceModel(null)
        val itemA = createResourceModel("Banana")
        val list = listOf(itemA, itemNull)

        val ascendingResult = viewModel.toggleTitleSortOrder(list)
        assertEquals(listOf(null, "Banana"), ascendingResult.map { it.item.title })

        val descendingResult = viewModel.toggleTitleSortOrder(ascendingResult)
        assertEquals(listOf("Banana", null), descendingResult.map { it.item.title })
    }

    @Test
    fun `toggleSortOrder sorts by date descending on first toggle and ascending on second toggle`() = runTest {
        val itemOld = createResourceModel("Old", 100L)
        val itemNew = createResourceModel("New", 300L)
        val itemMid = createResourceModel("Mid", 200L)
        val list = listOf(itemOld, itemNew, itemMid)

        val firstToggleResult = viewModel.toggleSortOrder(list)
        assertEquals(listOf(300L, 200L, 100L), firstToggleResult.map { it.item.createdDate })

        val secondToggleResult = viewModel.toggleSortOrder(firstToggleResult)
        assertEquals(listOf(100L, 200L, 300L), secondToggleResult.map { it.item.createdDate })
    }

    @Test
    fun `getFilterFacets extracts languages, subjects, mediums, and levels correctly`() = runTest {
        val lib1 = MyLibrary().apply {
            language = "English"
            subject = listOf("Math", "Science")
            mediaType = "PDF"
            level = listOf("Primary")
        }
        val lib2 = MyLibrary().apply {
            language = "Spanish"
            subject = listOf("Science", "History")
            mediaType = "Video"
            level = listOf("Secondary")
        }

        val facets = viewModel.getFilterFacets(listOf(lib1, lib2))

        assertEquals(setOf("English", "Spanish"), facets["languages"])
        assertEquals(setOf("Math", "Science", "History"), facets["subjects"])
        assertEquals(setOf("pdf", "video"), facets["mediums"])
        assertEquals(setOf("Primary", "Secondary"), facets["levels"])
    }


    @Test
    fun `getFilterFacets canonicalizes medium values into single canonical entries`() = runTest {
        val lib1 = MyLibrary().apply { mediaType = "video" }
        val lib2 = MyLibrary().apply { mediaType = "video/mp4" }
        val lib3 = MyLibrary().apply { mediaType = "mp4" }

        val facets = viewModel.getFilterFacets(listOf(lib1, lib2, lib3))

        assertEquals(setOf("video"), facets["mediums"])
    }
    @Test
    fun `filterIfChanged memoizes the criteria in the view model until resetFilter`() = runTest {
        val models = listOf(createResourceModel("a", 1), createResourceModel("b", 2))
        val notDownloaded = ResourcesFilterCriteria(
            searchQuery = "",
            searchTags = emptyList(),
            subjects = emptySet(),
            levels = emptySet(),
            languages = emptySet(),
            mediums = emptySet(),
            downloadFilterIndex = 2
        )

        val first = viewModel.filterIfChanged(models, notDownloaded, setOf("a"))
        val second = viewModel.filterIfChanged(models, notDownloaded, setOf("a"))
        viewModel.resetFilter()
        val afterReset = viewModel.filterIfChanged(models, notDownloaded, setOf("a"))

        assertEquals(listOf("b"), first?.map { it.item.id })
        assertNull(second)
        assertEquals(listOf("b"), afterReset?.map { it.item.id })
    }

    private fun createResourceModel(title: String?, createdDate: Long = 0L): ResourceListModel {
        val library = MyLibrary().apply {
            this.title = title
        }
        val item = ResourceItem(
            id = title ?: "null_id",
            title = title,
            description = null,
            createdDate = createdDate,
            averageRating = null,
            timesRated = 0,
            resourceId = title ?: "null_id",
            isOffline = false,
            _rev = null,
            uploadDate = null,
            filename = null
        )
        return ResourceListModel(library, item, emptyList())
    }

    @Test
    fun `notifyDownloadComplete delivers one event to a collector`() = runTest {
        val events = mutableListOf<Unit>()
        val job = launch { viewModel.downloadComplete.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.notifyDownloadComplete()
        advanceUntilIdle()

        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun `loadResources with empty state seeds from cache`() = runTest {
        val cached = listOf(createResourceModel("Cached Item", 100L))
        every { resourcesRepository.getCachedResourceListModels(true, "modelId") } returns cached
        coEvery { resourcesRepository.getResourceListModels(true, "modelId") } returns emptyList()

        viewModel.loadResources(true, "modelId")

        assertEquals(cached, viewModel.resourcesState.value)
        verify(exactly = 1) { resourcesRepository.getCachedResourceListModels(true, "modelId") }
    }

    @Test
    fun `loadResources with non-empty state does not call getCachedResourceListModels`() = runTest {
        val existingModel = createResourceModel("Existing Item", 100L)
        coEvery { resourcesRepository.getResourceListModels(true, "modelId") } returns listOf(existingModel)

        viewModel.loadResources(true, "modelId")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.resourcesState.value.size)

        clearMocks(resourcesRepository, verificationMarks = true, answers = false)

        viewModel.loadResources(true, "modelId")

        verify(exactly = 0) { resourcesRepository.getCachedResourceListModels(any(), any()) }
    }
}
