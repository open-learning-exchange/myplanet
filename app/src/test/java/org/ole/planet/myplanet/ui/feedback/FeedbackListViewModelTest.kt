package org.ole.planet.myplanet.ui.feedback

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: FeedbackListViewModel
    private lateinit var feedbackRepository: FeedbackRepository
    private lateinit var userRepository: UserRepository
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        feedbackRepository = mockk()
        userRepository = mockk()

        val user = mockk<UserEntity> {
            every { name } returns "testUser"
            every { isManager() } returns false
        }
        coEvery { userRepository.getUserModel() } returns user
        every { feedbackRepository.getFeedback("testUser", false) } returns flowOf(emptyList())

        viewModel = FeedbackListViewModel(
            feedbackRepository = feedbackRepository,
            userRepository = userRepository
        )
    }

    @Test
    fun testInitialStateIsPreloadEmptyList() = runTest(testDispatcher) {
        // This test validates the pre-load default state of the StateFlow before the init coroutine has executed
        assertEquals(emptyList<Feedback>(), viewModel.feedbackList.value)
    }

    @Test
    fun testFeedbackListEmitsDataFromFeedbackRepository() = runTest(testDispatcher) {
        val user = mockk<UserEntity> {
            every { name } returns "testUser"
            every { isManager() } returns false
        }
        val feedback1 = mockk<Feedback>()
        val feedback2 = mockk<Feedback>()
        val feedbackList = listOf(feedback1, feedback2)

        coEvery { userRepository.getUserModel() } returns user
        clearMocks(feedbackRepository, answers = false)
        every { feedbackRepository.getFeedback("testUser", false) } returns flowOf(feedbackList)

        // Recreate viewModel to trigger init block with new mock data
        viewModel = FeedbackListViewModel(
            feedbackRepository = feedbackRepository,
            userRepository = userRepository
        )

        advanceUntilIdle()

        assertEquals(feedbackList, viewModel.feedbackList.value)
        verify(exactly = 1) { feedbackRepository.getFeedback("testUser", false) }
    }

    @Test
    fun testRefreshFeedbackCancelsPreviousJobAndRetriggersFlowCollection() = runTest(testDispatcher) {
        val user = mockk<UserEntity> {
            every { name } returns "testUser"
            every { isManager() } returns false
        }
        val initialFeedback = listOf(mockk<Feedback>())
        val updatedFeedback = listOf(mockk<Feedback>(), mockk<Feedback>())

        coEvery { userRepository.getUserModel() } returns user
        clearMocks(feedbackRepository, answers = false)

        // First call returns initial list
        every { feedbackRepository.getFeedback("testUser", false) } returns flowOf(initialFeedback)

        // Init view model
        viewModel = FeedbackListViewModel(
            feedbackRepository = feedbackRepository,
            userRepository = userRepository
        )
        advanceUntilIdle()
        assertEquals(initialFeedback, viewModel.feedbackList.value)

        // Setup for refresh
        every { feedbackRepository.getFeedback("testUser", false) } returns flowOf(updatedFeedback)

        // Trigger refresh
        viewModel.refreshFeedback()
        advanceUntilIdle()

        assertEquals(updatedFeedback, viewModel.feedbackList.value)
        // Verify it was called twice: once in init, once in refreshFeedback
        verify(exactly = 2) { feedbackRepository.getFeedback("testUser", false) }
    }
}
