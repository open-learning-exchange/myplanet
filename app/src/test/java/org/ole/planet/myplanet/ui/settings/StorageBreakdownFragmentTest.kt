package org.ole.planet.myplanet.ui.settings

import android.content.Context
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.StorageBreakdown
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class StorageBreakdownFragmentTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val context = mockk<Context>(relaxed = true)
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setUp() {
        mockkObject(FileUtils)
        coEvery { resourcesRepository.getStorageBreakdown(any()) } answers {
            StorageBreakdown(0L, LongArray(StorageCategories.all.size), IntArray(StorageCategories.all.size))
        }
    }

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
    }

    private fun createViewModel(): StorageBreakdownViewModel {
        return StorageBreakdownViewModel(context, resourcesRepository, dispatcherProvider)
    }

    @Test
    fun `scan runs once across multiple loadStorage calls unless forced`() = runTest(testDispatcher) {
        val rootDir = tempFolder.newFolder("ole")
        every { FileUtils.getOlePath(any()) } returns rootDir.absolutePath
        every { FileUtils.availableOverTotalMemoryFormattedString(any()) } returns "10 GB / 32 GB"

        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(resourcesRepository, answers = false)

        // First collection of state flow
        val collectJob1 = backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Call loadStorage again without forceRefresh
        viewModel.loadStorage(forceRefresh = false)
        advanceUntilIdle()

        // Second collection of state flow
        val collectJob2 = backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Unforced loadStorage calls after initial scan do not re-trigger scanStorage
        coVerify(exactly = 0) { resourcesRepository.getStorageBreakdown(any()) }

        // Forced refresh triggers scanStorage
        viewModel.loadStorage(forceRefresh = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { resourcesRepository.getStorageBreakdown(any()) }

        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun `totalBytes zero emits empty state`() = runTest(testDispatcher) {
        val emptyDir = tempFolder.newFolder("empty_ole")
        every { FileUtils.getOlePath(any()) } returns emptyDir.absolutePath
        every { FileUtils.availableOverTotalMemoryFormattedString(any()) } returns "10 GB / 32 GB"

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0L, state.totalBytes)
        assertEquals("10 GB / 32 GB", state.availableSpaceText)
        assertEquals(StorageCategories.all.size, state.categories.size)
        state.categories.forEach { category ->
            assertEquals(0L, category.sizeBytes)
            assertEquals(0, category.fileCount)
        }
    }

    @Test
    fun `emitted CategoryData values line up with StorageCategories all by index`() = runTest(testDispatcher) {
        val rootDir = tempFolder.newFolder("ole_categories")
        File(rootDir, "vid.mp4").apply { writeBytes(ByteArray(100)) }
        File(rootDir, "doc.pdf").apply { writeBytes(ByteArray(300)) }

        every { FileUtils.getOlePath(any()) } returns rootDir.absolutePath
        every { FileUtils.availableOverTotalMemoryFormattedString(any()) } returns "5 GB / 16 GB"

        val sizes = LongArray(StorageCategories.all.size).apply {
            this[0] = 100L
            this[2] = 300L
        }
        val counts = IntArray(StorageCategories.all.size).apply {
            this[0] = 1
            this[2] = 1
        }
        coEvery { resourcesRepository.getStorageBreakdown(File(rootDir.absolutePath)) } returns StorageBreakdown(
            400L, sizes, counts
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StorageCategories.all.size, state.categories.size)

        state.categories.forEachIndexed { index, categoryData ->
            val expectedCategory = StorageCategories.all[index]
            assertEquals(expectedCategory.nameRes, categoryData.nameRes)
            assertEquals(expectedCategory.extensions, categoryData.extensions)

            if (index == 0) { // Videos
                assertEquals(100L, categoryData.sizeBytes)
                assertEquals(1, categoryData.fileCount)
            } else if (index == 2) { // PDFs
                assertEquals(300L, categoryData.sizeBytes)
                assertEquals(1, categoryData.fileCount)
            } else {
                assertEquals(0L, categoryData.sizeBytes)
                assertEquals(0, categoryData.fileCount)
            }
        }
    }
}
