package org.ole.planet.myplanet.ui.life

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class LifeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: LifeViewModel
    private lateinit var context: Context
    private lateinit var lifeRepository: LifeRepository
    private lateinit var userRepository: UserRepository
    private lateinit var dispatcherProvider: TestDispatcherProvider

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        lifeRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        dispatcherProvider = TestDispatcherProvider(testDispatcher)

        viewModel = LifeViewModel(
            context,
            lifeRepository,
            userRepository,
            dispatcherProvider
        )
    }

    @Test
    fun `loadMyLifeList emits items from repository when available`() = runTest {
        val userId = "user123"
        val item1 = MyLife().apply { _id = "1"; title = "Health"; imageId = "ic_myhealth"; this.userId = userId }
        val item2 = MyLife().apply { _id = "2"; title = "Calendar"; imageId = "ic_calendar"; this.userId = userId }

        coEvery { userRepository.getCurrentUserId() } returns userId
        coEvery { lifeRepository.getMyLifeByUserId(userId) } returns listOf(item1, item2)

        viewModel.loadMyLifeList()
        testScheduler.advanceUntilIdle()

        val list = viewModel.myLifeList.value
        assertEquals(2, list.size)
        assertEquals("Health", list[0].title)
        assertEquals("Calendar", list[1].title)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(userId) }
        coVerify(exactly = 0) { lifeRepository.seedMyLifeIfEmpty(any(), any()) }
    }

    @Test
    fun `loadMyLifeList seeds default items when repository is empty`() = runTest {
        val userId = "user123"
        val seededItem = MyLife().apply { _id = "1"; title = "Health"; imageId = "ic_myhealth"; this.userId = userId }

        coEvery { userRepository.getCurrentUserId() } returns userId
        coEvery { lifeRepository.getMyLifeByUserId(userId) } returnsMany listOf(emptyList(), listOf(seededItem))
        coEvery { lifeRepository.seedMyLifeIfEmpty(userId, any()) } returns Unit

        viewModel.loadMyLifeList()
        testScheduler.advanceUntilIdle()

        val list = viewModel.myLifeList.value
        assertEquals(1, list.size)
        assertEquals("Health", list[0].title)
        coVerify(exactly = 1) { lifeRepository.seedMyLifeIfEmpty(userId, any()) }
        coVerify(exactly = 2) { lifeRepository.getMyLifeByUserId(userId) }
    }

    @Test
    fun `loadMyLifeList falls back to null when no user is available`() = runTest {
        val seededItem = MyLife().apply { _id = "1"; title = "Health"; imageId = "ic_myhealth"; this.userId = null }

        coEvery { userRepository.getCurrentUserId() } returns null
        coEvery { userRepository.getUserModel() } returns null
        coEvery { lifeRepository.getMyLifeByUserId(null) } returns listOf(seededItem)

        viewModel.loadMyLifeList()
        testScheduler.advanceUntilIdle()

        val list = viewModel.myLifeList.value
        assertEquals(1, list.size)
        assertEquals("Health", list[0].title)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(null) }
    }

    @Test
    fun `loadMyLifeList treats placeholder userId as no user`() = runTest {
        val item = MyLife().apply { _id = "1"; title = "Health"; imageId = "ic_myhealth"; this.userId = null }

        coEvery { userRepository.getCurrentUserId() } returns "--"
        coEvery { lifeRepository.getMyLifeByUserId(null) } returns listOf(item)

        viewModel.loadMyLifeList()
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.myLifeList.value.size)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(null) }
    }

    @Test
    fun `loadMyLifeList falls back to user repository id when current userId is empty`() = runTest {
        val userModel = UserEntity("userFromRepo", name = "Test User")
        val item = MyLife().apply { _id = "1"; title = "Health"; imageId = "ic_myhealth"; this.userId = "userFromRepo" }

        coEvery { userRepository.getCurrentUserId() } returns ""
        coEvery { userRepository.getUserModel() } returns userModel
        coEvery { lifeRepository.getMyLifeByUserId("userFromRepo") } returns listOf(item)

        viewModel.loadMyLifeList()
        testScheduler.advanceUntilIdle()

        val list = viewModel.myLifeList.value
        assertEquals(1, list.size)
        assertEquals("Health", list[0].title)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId("userFromRepo") }
    }

    @Test
    fun `updateVisibility calls repository and reloads list`() = runTest {
        val userId = "user123"
        val item = MyLife().apply { _id = "1"; title = "Health"; imageId = "ic_myhealth"; this.userId = userId; isVisible = true }

        coEvery { userRepository.getCurrentUserId() } returns userId
        coEvery { lifeRepository.getMyLifeByUserId(userId) } returns listOf(item)
        coEvery { lifeRepository.updateVisibility(true, "1") } returns Unit

        viewModel.updateVisibility(true, "1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { lifeRepository.updateVisibility(true, "1") }
        coVerify(atLeast = 1) { lifeRepository.getMyLifeByUserId(userId) }
    }

    @Test
    fun `updateMyLifeListOrder calls repository and updates state flow`() = runTest {
        val items = listOf(MyLife().apply { _id = "1"; title = "Health" })
        coEvery { lifeRepository.updateMyLifeListOrder(items) } returns Unit

        viewModel.updateMyLifeListOrder(items)
        testScheduler.advanceUntilIdle()

        assertEquals(items, viewModel.myLifeList.value)
        coVerify(exactly = 1) { lifeRepository.updateMyLifeListOrder(items) }
    }
}
