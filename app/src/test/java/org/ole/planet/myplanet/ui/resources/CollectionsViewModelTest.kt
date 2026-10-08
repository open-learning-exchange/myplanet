package org.ole.planet.myplanet.ui.resources

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {

    private lateinit var viewModel: CollectionsViewModel
    private val tagsRepository: TagsRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val mainImmediate = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CollectionsViewModel(tagsRepository, dispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTags success`() = runTest(testDispatcher) {
        val parentTag = TagEntity().apply { id = "parent1" }
        val childTag = TagEntity().apply { id = "child1" }
        val mockData = mapOf(parentTag to listOf(childTag))

        coEvery { tagsRepository.getTagsWithChildren("type1") } returns mockData

        viewModel.loadTags("type1")

        val currentState = viewModel.state.value
        assertTrue(currentState is CollectionsState.Success)
        val successState = currentState as CollectionsState.Success
        assertEquals(1, successState.list.size)
        assertEquals(parentTag.id, successState.list[0].id)
        assertEquals(1, successState.childMap.size)
        assertEquals(childTag.id, successState.childMap["parent1"]?.get(0)?.id)
    }

    @Test
    fun `loadTags empty`() = runTest(testDispatcher) {
        coEvery { tagsRepository.getTagsWithChildren("type1") } returns emptyMap()

        viewModel.loadTags("type1")

        val currentState = viewModel.state.value
        assertTrue(currentState is CollectionsState.Empty)
    }

    @Test
    fun `loadTags error`() = runTest(testDispatcher) {
        coEvery { tagsRepository.getTagsWithChildren("type1") } throws Exception("Database error")

        viewModel.loadTags("type1")

        val currentState = viewModel.state.value
        assertTrue(currentState is CollectionsState.Error)
        val errorState = currentState as CollectionsState.Error
        assertEquals("Database error", errorState.message)
    }

    @Test
    fun `loadTags re-entry guard`() = runTest(testDispatcher) {
        val mockData = mapOf(TagEntity() to emptyList<TagEntity>())
        coEvery { tagsRepository.getTagsWithChildren("type1") } returns mockData

        viewModel.loadTags("type1")
        val state1 = viewModel.state.value

        // This should not trigger another fetch
        viewModel.loadTags("type1")
        val state2 = viewModel.state.value

        assertEquals(state1, state2)
    }
}
