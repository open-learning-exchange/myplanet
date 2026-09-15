package org.ole.planet.myplanet.ui.dictionary

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.DictionaryLoad
import org.ole.planet.myplanet.repository.DictionaryRepository
import org.ole.planet.myplanet.repository.DictionaryWord

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {

    private lateinit var viewModel: DictionaryViewModel
    private val dictionaryRepository = mockk<DictionaryRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DictionaryViewModel(dictionaryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDictionary emits Populated with count when data is inserted`() = runTest(testDispatcher) {
        coEvery { dictionaryRepository.insertDictionaryData() } returns DictionaryLoad.Inserted
        coEvery { dictionaryRepository.count() } returns 42L

        viewModel.loadDictionary()
        advanceUntilIdle()

        val state = viewModel.loadState.value
        assertTrue(state is DictionaryLoadState.Populated)
        assertEquals(42L, (state as DictionaryLoadState.Populated).count)
    }

    @Test
    fun `loadDictionary emits Populated when data is already populated`() = runTest(testDispatcher) {
        coEvery { dictionaryRepository.insertDictionaryData() } returns DictionaryLoad.AlreadyPopulated
        coEvery { dictionaryRepository.count() } returns 7L

        viewModel.loadDictionary()
        advanceUntilIdle()

        val state = viewModel.loadState.value
        assertTrue(state is DictionaryLoadState.Populated)
        assertEquals(7L, (state as DictionaryLoadState.Populated).count)
    }

    @Test
    fun `loadDictionary emits FileMissing when dictionary file is missing`() = runTest(testDispatcher) {
        coEvery { dictionaryRepository.insertDictionaryData() } returns DictionaryLoad.FileMissing

        viewModel.loadDictionary()
        advanceUntilIdle()

        assertTrue(viewModel.loadState.value is DictionaryLoadState.FileMissing)
        coVerify(exactly = 0) { dictionaryRepository.count() }
    }

    @Test
    fun `loadDictionary emits Failed when insertDictionaryData fails`() = runTest(testDispatcher) {
        val cause = RuntimeException("parse error")
        coEvery { dictionaryRepository.insertDictionaryData() } returns DictionaryLoad.Failed(cause)

        viewModel.loadDictionary()
        advanceUntilIdle()

        val state = viewModel.loadState.value
        assertTrue(state is DictionaryLoadState.Failed)
        assertEquals(cause, (state as DictionaryLoadState.Failed).cause)
    }

    @Test
    fun `loadCount emits Populated with count without inserting`() = runTest(testDispatcher) {
        coEvery { dictionaryRepository.count() } returns 100L

        viewModel.loadCount()
        advanceUntilIdle()

        val state = viewModel.loadState.value
        assertTrue(state is DictionaryLoadState.Populated)
        assertEquals(100L, (state as DictionaryLoadState.Populated).count)
        coVerify(exactly = 0) { dictionaryRepository.insertDictionaryData() }
    }

    @Test
    fun `searchWord emits Found when entry exists`() = runTest(testDispatcher) {
        val entry = DictionaryWord(word = "hello", definition = "greeting", synonym = "hi", antonym = "bye")
        coEvery { dictionaryRepository.findByWord("hello") } returns entry

        viewModel.searchWord("hello")
        advanceUntilIdle()

        val state = viewModel.searchState.value
        assertTrue(state is DictionarySearchState.Found)
        assertEquals("hello", (state as DictionarySearchState.Found).entry.word)
    }

    @Test
    fun `searchWord emits NotFound when entry does not exist`() = runTest(testDispatcher) {
        coEvery { dictionaryRepository.findByWord(any()) } returns null

        viewModel.searchWord("missing")
        advanceUntilIdle()

        assertTrue(viewModel.searchState.value is DictionarySearchState.NotFound)
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.loadState.value is DictionaryLoadState.Idle)
        assertTrue(viewModel.searchState.value is DictionarySearchState.Idle)
    }
}
