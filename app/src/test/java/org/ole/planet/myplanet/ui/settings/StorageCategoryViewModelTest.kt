package org.ole.planet.myplanet.ui.settings

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCategoryViewModelTest {

    private lateinit var viewModel: StorageCategoryViewModel
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val context = mockk<Context>()
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val items = listOf(
        OfflineResourceItem("r1", "Resource 1", listOf("/ole/r1/a.mp4"), 100),
        OfflineResourceItem("r2", "Resource 2", listOf("/ole/r2/b.pdf"), 200),
        OfflineResourceItem("r3", "Resource 3", listOf("/ole/r3/c.mp4"), 300),
    )

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        mockkObject(FileUtils)
        every { FileUtils.getOlePath(any()) } returns "/ole"
        coEvery {
            resourcesRepository.getOfflineResourceItems(any(), any(), any())
        } returns items
        viewModel = StorageCategoryViewModel(resourcesRepository, dispatcherProvider, context)
    }

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
        Dispatchers.resetMain()
    }

    @Test
    fun `loadResources populates items and clears loading`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(items, state.items)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `toggleItemChecked flips a single item and updates checkedCount`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.checkedCount)

        viewModel.toggleItemChecked("r1")

        val state = viewModel.uiState.value
        assertTrue(state.items.first { it.resourceId == "r1" }.isChecked)
        assertEquals(1, state.checkedCount)
    }

    @Test
    fun `toggleAllChecked checks all then unchecks all`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleAllChecked()
        assertEquals(items.size, viewModel.uiState.value.checkedCount)
        assertTrue(viewModel.uiState.value.allChecked)

        viewModel.toggleAllChecked()
        assertEquals(0, viewModel.uiState.value.checkedCount)
        assertFalse(viewModel.uiState.value.allChecked)
    }

    @Test
    fun `deleteSelected deletes only checked items`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleItemChecked("r1")
        viewModel.deleteSelected()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            resourcesRepository.deleteOfflineResources(
                "/ole",
                match { list -> list.size == 1 && list.first().resourceId == "r1" }
            )
        }
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `deleteSelected with no checked items does not delete`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteSelected()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            resourcesRepository.deleteOfflineResources(any(), any())
        }
    }

    @Test
    fun `deleteAll deletes every loaded item`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteAll()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            resourcesRepository.deleteOfflineResources("/ole", match { it.size == items.size })
        }
    }

    @Test
    fun `deleteAll with no items does not delete`() = runTest {
        coEvery {
            resourcesRepository.getOfflineResourceItems(any(), any(), any())
        } returns emptyList()
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteAll()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            resourcesRepository.deleteOfflineResources(any(), any())
        }
    }

    @Test
    fun `delete emits deleteCompleteEvent`() = runTest {
        viewModel.loadResources(setOf("mp4"), setOf("pdf"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteAll()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Unit, viewModel.deleteCompleteEvent.first())
    }
}
