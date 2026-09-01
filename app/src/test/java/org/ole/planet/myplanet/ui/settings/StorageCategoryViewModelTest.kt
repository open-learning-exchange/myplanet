package org.ole.planet.myplanet.ui.settings

import android.content.Context
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCategoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var viewModel: StorageCategoryViewModel
    private val olePath = "/tmp/ole/"

    @Before
    fun setUp() {
        mockkObject(FileUtils)
        every { FileUtils.getOlePath(any()) } returns olePath
        viewModel = StorageCategoryViewModel(resourcesRepository, dispatcherProvider, context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `toggleItemChecked flips only the matching item's checked state`() = runTest(testDispatcher) {
        val items = listOf(
            OfflineResourceItem("res1", "Book", listOf("/p/b.pdf"), 100L, false),
            OfflineResourceItem("res2", "Video", listOf("/p/v.mp4"), 200L, false)
        )
        loadItems(items)

        viewModel.toggleItemChecked("res1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.items.first { it.resourceId == "res1" }.isChecked)
        assertFalse(state.items.first { it.resourceId == "res2" }.isChecked)

        viewModel.toggleItemChecked("res1")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.items.first { it.resourceId == "res1" }.isChecked)
    }

    @Test
    fun `toggleAllChecked checks all items when not all checked and unchecks when all checked`() = runTest(testDispatcher) {
        val items = listOf(
            OfflineResourceItem("res1", "Book", listOf("/p/b.pdf"), 100L, false),
            OfflineResourceItem("res2", "Video", listOf("/p/v.mp4"), 200L, true)
        )
        loadItems(items)

        viewModel.toggleAllChecked()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.items.all { it.isChecked })

        viewModel.toggleAllChecked()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.items.none { it.isChecked })
    }

    @Test
    fun `deleteItems re-entry guard prevents a second deletion while one is in flight`() = runTest(testDispatcher) {
        val items = listOf(
            OfflineResourceItem("res1", "Book", listOf("/p/b.pdf"), 100L, true)
        )
        loadItems(items)

        coEvery { resourcesRepository.deleteOfflineResources(olePath, items) } just Runs

        viewModel.deleteItems(items)
        // The first call sets isDeleting synchronously; without advancing, a second call hits the guard.
        assertTrue(viewModel.uiState.value.isDeleting)

        viewModel.deleteItems(items)
        advanceUntilIdle()

        assertFalse("isDeleting should be cleared after deletion completes", viewModel.uiState.value.isDeleting)
        coVerify(exactly = 1) { resourcesRepository.deleteOfflineResources(olePath, items) }
    }

    @Test
    fun `deleteItems emits a complete event after deleting`() = runTest(testDispatcher) {
        val items = listOf(
            OfflineResourceItem("res1", "Book", listOf("/p/b.pdf"), 100L, true)
        )
        loadItems(items)

        coEvery { resourcesRepository.deleteOfflineResources(olePath, items) } just Runs

        viewModel.deleteItems(items)
        advanceUntilIdle()

        assertEquals(Unit, viewModel.deleteCompleteEvent.first())
    }

    private suspend fun TestScope.loadItems(items: List<OfflineResourceItem>) {
        coEvery {
            resourcesRepository.getOfflineResourceItems(olePath, any(), any())
        } returns items
        viewModel.loadResources(emptySet(), emptySet())
        advanceUntilIdle()
    }
}
