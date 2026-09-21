package org.ole.planet.myplanet.ui.settings

import android.content.Context
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
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
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setUp() {
        mockkObject(FileUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
    }

    private fun createViewModel(): StorageBreakdownViewModel {
        return StorageBreakdownViewModel(context, dispatcherProvider)
    }

    @Test
    fun `scanStorage produces identical counts and total sizes for fixture tree`() {
        val rootDir = tempFolder.newFolder("ole")

        // Create test files
        // Videos (Index 0): mp4 (100 bytes), MKV (uppercase, 200 bytes)
        val file1 = File(rootDir, "video1.mp4").apply { writeBytes(ByteArray(100)) }
        val file2 = File(rootDir, "video2.MKV").apply { writeBytes(ByteArray(200)) }

        // Audio (Index 1): MP3 (uppercase, 50 bytes)
        val subDir = File(rootDir, "audio_folder").apply { mkdirs() }
        val file3 = File(subDir, "song.MP3").apply { writeBytes(ByteArray(50)) }

        // PDFs (Index 2): pdf (300 bytes)
        val file4 = File(rootDir, "document.pdf").apply { writeBytes(ByteArray(300)) }

        // Images (Index 3): PNG (uppercase, 40 bytes)
        val file5 = File(rootDir, "image.PNG").apply { writeBytes(ByteArray(40)) }

        // Other (Index 4): txt (unknown extension, 80 bytes), no extension (20 bytes)
        val file6 = File(rootDir, "notes.txt").apply { writeBytes(ByteArray(80)) }
        val file7 = File(rootDir, "README").apply { writeBytes(ByteArray(20)) }

        val viewModel = createViewModel()
        val result = viewModel.scanStorage(rootDir)

        val expectedTotal = 100L + 200L + 50L + 300L + 40L + 80L + 20L
        assertEquals(expectedTotal, result.totalBytes)

        // Videos: 2 files, 300 bytes
        assertEquals(2, result.counts[0])
        assertEquals(300L, result.sizes[0])

        // Audio: 1 file, 50 bytes
        assertEquals(1, result.counts[1])
        assertEquals(50L, result.sizes[1])

        // PDFs: 1 file, 300 bytes
        assertEquals(1, result.counts[2])
        assertEquals(300L, result.sizes[2])

        // Images: 1 file, 40 bytes
        assertEquals(1, result.counts[3])
        assertEquals(40L, result.sizes[3])

        // Other: 2 files, 100 bytes
        assertEquals(2, result.counts[4])
        assertEquals(100L, result.sizes[4])
    }

    @Test
    fun `scanStorage handles empty or nonexistent directory`() {
        val viewModel = createViewModel()

        val emptyDir = tempFolder.newFolder("empty")
        val emptyResult = viewModel.scanStorage(emptyDir)
        assertEquals(0L, emptyResult.totalBytes)
        assertEquals(0, emptyResult.counts.sum())

        val nonExistentDir = File(tempFolder.root, "non_existent")
        val nonExistentResult = viewModel.scanStorage(nonExistentDir)
        assertEquals(0L, nonExistentResult.totalBytes)
        assertEquals(0, nonExistentResult.counts.sum())
    }

    @Test
    fun `scan runs once across multiple loadStorage calls unless forced`() = runTest(testDispatcher) {
        val rootDir = tempFolder.newFolder("ole")
        every { FileUtils.getOlePath(any()) } returns rootDir.absolutePath
        every { FileUtils.availableOverTotalMemoryFormattedString(any()) } returns "10 GB / 32 GB"

        val viewModel = spyk(StorageBreakdownViewModel(context, dispatcherProvider))
        advanceUntilIdle()
        clearMocks(viewModel, answers = false)

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
        verify(exactly = 0) { viewModel.scanStorage(any<File>()) }

        // Forced refresh triggers scanStorage
        viewModel.loadStorage(forceRefresh = true)
        advanceUntilIdle()

        verify(exactly = 1) { viewModel.scanStorage(any<File>()) }

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
