package org.ole.planet.myplanet.ui.life

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
    fun `loadMyLifeList resolves userId via userRepository and loads myLifeList`() = runTest {
        val userEntity = UserEntity(id = "user_123")
        coEvery { userRepository.getUserModel() } returns userEntity
        val item = MyLife("img1", "user_123", "Item 1")
        coEvery { lifeRepository.getMyLifeByUserId("user_123") } returns listOf(item)

        viewModel.loadMyLifeList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("user_123") }
        coVerify(exactly = 0) { lifeRepository.seedMyLifeIfEmpty(any(), any()) }
    }

    @Test
    fun `loadMyLifeList seeds list if empty`() = runTest {
        val userEntity = UserEntity(id = "user_123")
        coEvery { userRepository.getUserModel() } returns userEntity
        val item = MyLife("img1", "user_123", "Item 1")
        coEvery { lifeRepository.getMyLifeByUserId("user_123") } returnsMany listOf(emptyList(), listOf(item))

        viewModel.loadMyLifeList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item), viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.seedMyLifeIfEmpty("user_123", any()) }
        coVerify(exactly = 2) { lifeRepository.getMyLifeByUserId("user_123") }
    }

    @Test
    fun `updateVisibility calls repository and reloads list`() = runTest {
        val userEntity = UserEntity(id = "user_123")
        coEvery { userRepository.getUserModel() } returns userEntity
        coEvery { lifeRepository.getMyLifeByUserId("user_123") } returns emptyList()

        viewModel.updateVisibility(true, "item_1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { lifeRepository.updateVisibility(true, "item_1") }
        coVerify { lifeRepository.getMyLifeByUserId("user_123") }
    }

    @Test
    fun `updateMyLifeListOrder calls repository`() = runTest {
        val list = listOf(MyLife("img1", "user_123", "Item 1"))
        viewModel.updateMyLifeListOrder(list)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { lifeRepository.updateMyLifeListOrder(list) }
    }
}
