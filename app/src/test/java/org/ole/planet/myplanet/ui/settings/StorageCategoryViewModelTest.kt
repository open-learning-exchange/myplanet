package org.ole.planet.myplanet.ui.settings

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCategoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var viewModel: StorageCategoryViewModel
    private val olePath = "/tmp/ole/"

    private val items = listOf(
        OfflineResourceItem("r1", "Resource 1", listOf("/p/a.mp4"), 100L),
        OfflineResourceItem("r2", "Resource 2", listOf("/p/b.pdf"), 200L),
        OfflineResourceItem("r3", "Resource 3", listOf("/p/c.mp4"), 300L),
    )

    @Before
    fun setUp() {
        viewModel = StorageCategoryViewModel(resourcesRepository, dispatcherProvider)
    }

    @Test
    fun `loadResources populates items and clears loading`() = runTest(testDispatcher) {
        loadItems(items)

        val state = viewModel.uiState.value
        assertEquals(items, state.items)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `loadResources marks state empty when nothing is stored`() = runTest(testDispatcher) {
        loadItems(emptyList())

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `toggleItemChecked flips only the matching item and updates checkedCount`() = runTest(testDispatcher) {
        loadItems(items)

        assertEquals(0, viewModel.uiState.value.checkedCount)

        viewModel.toggleItemChecked("r1")

        val state = viewModel.uiState.value
        assertTrue(state.items.first { it.resourceId == "r1" }.isChecked)
        assertFalse(state.items.first { it.resourceId == "r2" }.isChecked)
        assertEquals(1, state.checkedCount)

        viewModel.toggleItemChecked("r1")
        assertFalse(viewModel.uiState.value.items.first { it.resourceId == "r1" }.isChecked)
        assertEquals(0, viewModel.uiState.value.checkedCount)
    }

    @Test
    fun `toggleAllChecked checks all then unchecks all`() = runTest(testDispatcher) {
        loadItems(items)

        viewModel.toggleAllChecked()
        assertEquals(items.size, viewModel.uiState.value.checkedCount)
        assertTrue(viewModel.uiState.value.allChecked)

        viewModel.toggleAllChecked()
        assertEquals(0, viewModel.uiState.value.checkedCount)
        assertFalse(viewModel.uiState.value.allChecked)
    }

    @Test
    fun `toggleAllChecked checks every item when only some are checked`() = runTest(testDispatcher) {
        loadItems(
            listOf(
                OfflineResourceItem("r1", "Resource 1", listOf("/p/a.mp4"), 100L, false),
                OfflineResourceItem("r2", "Resource 2", listOf("/p/b.pdf"), 200L, true),
            )
        )

        viewModel.toggleAllChecked()
        assertTrue(viewModel.uiState.value.items.all { it.isChecked })

        viewModel.toggleAllChecked()
        assertTrue(viewModel.uiState.value.items.none { it.isChecked })
    }

    @Test
    fun `deleteSelected deletes only checked items`() = runTest(testDispatcher) {
        loadItems(items)

        viewModel.toggleItemChecked("r1")
        viewModel.deleteSelected(olePath)
        advanceUntilIdle()

        coVerify {
            resourcesRepository.deleteOfflineResources(
                olePath,
                match { list -> list.size == 1 && list.first().resourceId == "r1" }
            )
        }
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `deleteSelected with no checked items does not delete`() = runTest(testDispatcher) {
        loadItems(items)

        viewModel.deleteSelected(olePath)
        advanceUntilIdle()

        coVerify(exactly = 0) { resourcesRepository.deleteOfflineResources(any(), any()) }
    }

    @Test
    fun `deleteAll deletes every loaded item`() = runTest(testDispatcher) {
        loadItems(items)

        viewModel.deleteAll(olePath)
        advanceUntilIdle()

        coVerify {
            resourcesRepository.deleteOfflineResources(olePath, match { it.size == items.size })
        }
    }

    @Test
    fun `deleteAll with no items does not delete`() = runTest(testDispatcher) {
        loadItems(emptyList())

        viewModel.deleteAll(olePath)
        advanceUntilIdle()

        coVerify(exactly = 0) { resourcesRepository.deleteOfflineResources(any(), any()) }
    }

    @Test
    fun `re-entry guard prevents a second deletion while one is in flight`() = runTest(testDispatcher) {
        loadItems(items)
        coEvery { resourcesRepository.deleteOfflineResources(olePath, items) } just Runs

        viewModel.deleteAll(olePath)
        // The first call sets isDeleting synchronously; without advancing, a second call hits the guard.
        assertTrue(viewModel.uiState.value.isDeleting)

        viewModel.deleteAll(olePath)
        advanceUntilIdle()

        assertFalse("isDeleting should be cleared after deletion completes", viewModel.uiState.value.isDeleting)
        coVerify(exactly = 1) { resourcesRepository.deleteOfflineResources(olePath, items) }
    }

    @Test
    fun `delete emits deleteCompleteEvent`() = runTest(testDispatcher) {
        loadItems(items)

        viewModel.deleteAll(olePath)
        advanceUntilIdle()

        assertEquals(Unit, viewModel.deleteCompleteEvent.first())
    }

    private fun TestScope.loadItems(loaded: List<OfflineResourceItem>) {
        coEvery {
            resourcesRepository.getOfflineResourceItems(olePath, any(), any())
        } returns loaded
        viewModel.loadResources(olePath, emptySet(), emptySet())
        advanceUntilIdle()
    }
}
