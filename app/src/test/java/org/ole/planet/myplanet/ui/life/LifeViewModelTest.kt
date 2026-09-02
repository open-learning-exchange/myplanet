package org.ole.planet.myplanet.ui.life

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class LifeViewModelTest {

    private lateinit var context: Context
    private lateinit var lifeRepository: LifeRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: LifeViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        lifeRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)

        viewModel = LifeViewModel(
            context,
            lifeRepository,
            userRepository,
            testDispatcherProvider
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

        viewModel.loadMyLifeList()
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

        viewModel.loadMyLifeList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        assertEquals(
            MyLife.defaultItems("user_123", context::getString).map { it.imageId },
            defaults.captured.map { it.imageId }
        )
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("user_123", any()) }
        coVerify(exactly = 0) { lifeRepository.seedMyLifeIfEmpty(any(), any()) }
    }

    @Test
    fun `updateVisibility calls repository and reloads list`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "user_123"
        coEvery { lifeRepository.getMyLifeByUserId("user_123", any()) } returns emptyList()

        viewModel.updateVisibility(true, "item_1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { lifeRepository.updateVisibility(true, "item_1") }
        coVerify { lifeRepository.getMyLifeByUserId("user_123", any()) }
    }

    @Test
    fun `updateMyLifeListOrder calls repository and updates state flow`() = runTest {
        val list = listOf(MyLife("img1", "user_123", "Item 1"))
        viewModel.updateMyLifeListOrder(list)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(list, viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.updateMyLifeListOrder(list) }
    }

    @Test
    fun `loadMyLifeList treats placeholder userId as no user`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns "--"
        val item = MyLife("img1", null, "Item 1")
        coEvery { lifeRepository.getMyLifeByUserId(null, any()) } returns listOf(item)

        viewModel.loadMyLifeList()
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

        viewModel.loadMyLifeList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("userFromRepo", any()) }
    }

    @Test
    fun `loadMyLifeList falls back to no user when neither source has an id`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns null
        coEvery { userRepository.getUserModel() } returns null
        coEvery { lifeRepository.getMyLifeByUserId(null, any()) } returns emptyList()

        viewModel.loadMyLifeList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<MyLife>(), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(null, any()) }
    }
}
