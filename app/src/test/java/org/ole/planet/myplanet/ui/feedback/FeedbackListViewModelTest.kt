package org.ole.planet.myplanet.ui.feedback

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: FeedbackListViewModel
    private lateinit var feedbackRepository: FeedbackRepository
    private lateinit var userSessionManager: UserSessionManager
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        feedbackRepository = mockk()
        userSessionManager = mockk()

        val user = mockk<RealmUser>()
        coEvery { userSessionManager.getUserModel() } returns user
        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(emptyList())

        viewModel = FeedbackListViewModel(
            feedbackRepository = feedbackRepository,
            userSessionManager = userSessionManager,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun testInitialStateIsPreloadEmptyList() = runTest(testDispatcher) {
        // This test validates the pre-load default state of the StateFlow before the init coroutine has executed
        assertEquals(emptyList<RealmFeedback>(), viewModel.feedbackList.value)
    }

    @Test
    fun testFeedbackListEmitsDataFromFeedbackRepository() = runTest(testDispatcher) {
        val user = mockk<RealmUser>()
        val feedback1 = mockk<RealmFeedback>()
        val feedback2 = mockk<RealmFeedback>()
        val feedbackList = listOf(feedback1, feedback2)

        coEvery { userSessionManager.getUserModel() } returns user
        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(feedbackList)

        // Recreate viewModel to trigger init block with new mock data
        viewModel = FeedbackListViewModel(
            feedbackRepository = feedbackRepository,
            userSessionManager = userSessionManager,
            dispatcherProvider = dispatcherProvider
        )

        advanceUntilIdle()

        assertEquals(feedbackList, viewModel.feedbackList.value)
        coVerify(exactly = 1) { feedbackRepository.getFeedback(user) }
    }

    @Test
    fun testRefreshFeedbackCancelsPreviousJobAndRetriggersFlowCollection() = runTest(testDispatcher) {
        val user = mockk<RealmUser>()
        val initialFeedback = listOf(mockk<RealmFeedback>())
        val updatedFeedback = listOf(mockk<RealmFeedback>(), mockk<RealmFeedback>())

        coEvery { userSessionManager.getUserModel() } returns user

        // First call returns initial list
        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(initialFeedback)

        // Init view model
        viewModel = FeedbackListViewModel(
            feedbackRepository = feedbackRepository,
            userSessionManager = userSessionManager,
            dispatcherProvider = dispatcherProvider
        )
        advanceUntilIdle()
        assertEquals(initialFeedback, viewModel.feedbackList.value)

        // Setup for refresh
        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(updatedFeedback)

        // Trigger refresh
        viewModel.refreshFeedback()
        advanceUntilIdle()

        assertEquals(updatedFeedback, viewModel.feedbackList.value)
        // Verify it was called twice: once in init, once in refreshFeedback
        coVerify(exactly = 2) { feedbackRepository.getFeedback(user) }
    }
}
