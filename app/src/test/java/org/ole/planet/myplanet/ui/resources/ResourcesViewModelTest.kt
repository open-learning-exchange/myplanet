package org.ole.planet.myplanet.ui.resources

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesViewModelTest {

    private lateinit var viewModel: ResourcesViewModel
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ResourcesViewModel(
            resourcesRepository,
            userRepository,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `getLibraryListModels maps enriched libraries to ResourceListModels`() = runTest {
        val mockLibrary = MyLibrary().apply {
            id = "lib1"
            title = "Library 1"
            resourceOffline = true
        }
        val mockRating = mockk<JsonObject>(relaxed = true)
        val mockTag = TagEntity().apply {
            id = "tag1"
            name = "Tag 1"
        }
        val mockResourceItem = mockk<ResourceItem>(relaxed = true)
        coEvery { resourcesRepository.getResourceListModels(any(), any()) } returns listOf(
            ResourceListModel(mockLibrary, mockResourceItem, mockRating, listOf(TagItem(mockTag.id, mockTag.name)))
        )

        val result = viewModel.getLibraryListModels(true, "modelId")

        assertEquals(1, result.size)
        assertEquals("lib1", result[0].library.id)
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

    private fun createResourceModel(title: String, createdDate: Long): ResourceListModel {
        val library = MyLibrary().apply {
            this.title = title
        }
        val item = ResourceItem(
            id = title,
            title = title,
            description = null,
            createdDate = createdDate,
            averageRating = null,
            timesRated = 0,
            resourceId = title,
            isOffline = false,
            _rev = null,
            uploadDate = null,
            filename = null
        )
        return ResourceListModel(library, item, null, emptyList<TagItem>())
    }
}
