package org.ole.planet.myplanet.ui.life

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LifeViewModelTest {

    private lateinit var lifeRepository: LifeRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: LifeViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val labelResolver: (Int) -> String = { "mock_string_$it" }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        lifeRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)

        viewModel = LifeViewModel(
            lifeRepository,
            userRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMyLifeList resolves userId via userRepository getCurrentUserId and loads myLifeList`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val item = MyLife("img1", "user_123", "Item 1")
        coEvery { lifeRepository.getMyLifeByUserId("user_123", any()) } returns listOf(item)

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("user_123", any()) }
        coVerify(exactly = 0) { lifeRepository.seedMyLifeIfEmpty(any(), any()) }
    }

    @Test
    fun `loadMyLifeList delegates seeding to the repository by passing the default items`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val item = MyLife("img1", "user_123", "Item 1")
        val defaults = slot<List<MyLife>>()
        coEvery { lifeRepository.getMyLifeByUserId("user_123", capture(defaults)) } returns listOf(item)

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        assertEquals(
            LifeItemDefaults.forUser("user_123", labelResolver).map { it.imageId },
            defaults.captured.map { it.imageId }
        )
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("user_123", any()) }
        coVerify(exactly = 0) { lifeRepository.seedMyLifeIfEmpty(any(), any()) }
    }

    @Test
    fun `updateVisibility calls repository write without re-querying or assigning returned list`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val updatedItem = MyLife("img1", "user_123", "Item 1").apply { isVisible = true }
        coEvery { lifeRepository.updateVisibility(true, "item_1", "user_123") } returns listOf(updatedItem)

        viewModel.updateVisibility(true, "item_1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { lifeRepository.updateVisibility(true, "item_1", "user_123") }
        coVerify(exactly = 0) { lifeRepository.getMyLifeByUserId(any(), any()) }
    }

    @Test
    fun `myLifeList follows repository flow emissions`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val initialItem = MyLife("img1", "user_123", "Item 1")
        val flowItem = MyLife("img2", "user_123", "Item 2")
        val flow = MutableStateFlow(listOf(flowItem))

        coEvery { lifeRepository.getMyLifeByUserId("user_123", any()) } returns listOf(initialItem)
        every { lifeRepository.observeMyLifeByUserId("user_123") } returns flow

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(flowItem), viewModel.myLifeList.value)

        val newItem = MyLife("img3", "user_123", "Item 3")
        flow.value = listOf(newItem)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(newItem), viewModel.myLifeList.value)
    }

    @Test
    fun `reorder followed by equal flow emission does not re-emit duplicate state`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val item1 = MyLife("img1", "user_123", "Item 1").apply { _id = "1"; weight = 0 }
        val item2 = MyLife("img2", "user_123", "Item 2").apply { _id = "2"; weight = 1 }
        val flow = MutableStateFlow(listOf(item1, item2))

        coEvery { lifeRepository.getMyLifeByUserId("user_123", any()) } returns listOf(item1, item2)
        every { lifeRepository.observeMyLifeByUserId("user_123") } returns flow

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        val reorderedList = listOf(item2, item1)
        viewModel.updateMyLifeListOrder(reorderedList)

        flow.value = reorderedList
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(reorderedList, viewModel.myLifeList.value)
    }

    @Test
    fun `loadMyLifeList cancels previous flow collection job when called again`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val flow1 = MutableStateFlow(listOf(MyLife("img1", "user_123", "Item 1")))
        val flow2 = MutableStateFlow(listOf(MyLife("img2", "user_123", "Item 2")))

        coEvery { lifeRepository.getMyLifeByUserId("user_123", any()) } returns emptyList()
        every { lifeRepository.observeMyLifeByUserId("user_123") } returns flow1

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        every { lifeRepository.observeMyLifeByUserId("user_123") } returns flow2
        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        flow1.value = listOf(MyLife("img1_updated", "user_123", "Item 1 updated"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(MyLife("img2", "user_123", "Item 2")), viewModel.myLifeList.value)
    }

    @Test
    fun `updateMyLifeListOrder calls repository and updates state flow`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        val list = listOf(MyLife("img1", "user_123", "Item 1"))
        viewModel.updateMyLifeListOrder(list)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(list, viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.updateMyLifeListOrder(list, "user_123") }
    }

    @Test
    fun `loadMyLifeList treats placeholder userId as no user`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "--"
        val item = MyLife("img1", null, "Item 1")
        coEvery { lifeRepository.getMyLifeByUserId(null, any()) } returns listOf(item)

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(null, any()) }
    }

    @Test
    fun `loadMyLifeList falls back to user repository id when current userId is empty`() = runTest {
        val item = MyLife("img1", "userFromRepo", "Item 1")
        coEvery { userRepository.getCurrentUserId() } returns ""
        coEvery { userRepository.getUserModel() } returns UserEntity("userFromRepo", name = "Test User")
        coEvery { lifeRepository.getMyLifeByUserId("userFromRepo", any()) } returns listOf(item)

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("userFromRepo", any()) }
    }

    @Test
    fun `loadMyLifeList falls back to no user when neither source has an id`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns null
        coEvery { userRepository.getUserModel() } returns null
        coEvery { lifeRepository.getMyLifeByUserId(null, any()) } returns emptyList()

        viewModel.loadMyLifeList(labelResolver)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<MyLife>(), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(null, any()) }
    }
}
