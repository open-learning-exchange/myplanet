package org.ole.planet.myplanet.ui.resources

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
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesViewModelTest {

    private lateinit var viewModel: ResourcesViewModel
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val serverUrlMapper = mockk<ServerUrlMapper>(relaxed = true)
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ResourcesViewModel(
            syncManager,
            sharedPrefManager,
            serverUrlMapper,
            resourcesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
}
