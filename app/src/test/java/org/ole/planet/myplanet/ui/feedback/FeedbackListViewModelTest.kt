package org.ole.planet.myplanet.ui.feedback

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val feedbackRepository: FeedbackRepository = mockk()
    private val userSessionManager: UserSessionManager = mockk()

    @Test
    fun `init loads feedback`() = runTest {
        val user = mockk<RealmUser>()
        coEvery { userSessionManager.getUserModel() } returns user

        val feedback1 = mockk<RealmFeedback>()
        val feedbackList1 = listOf(feedback1)

        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(feedbackList1)

        val viewModel = FeedbackListViewModel(feedbackRepository, userSessionManager)

        advanceUntilIdle()

        assertEquals(feedbackList1, viewModel.feedbackList.value)
    }

    @Test
    fun `flow emission updates feedbackList`() = runTest {
        val user = mockk<RealmUser>()
        coEvery { userSessionManager.getUserModel() } returns user

        val feedback1 = mockk<RealmFeedback>()
        val feedbackList1 = listOf(feedback1)

        val feedback2 = mockk<RealmFeedback>()
        val feedbackList2 = listOf(feedback1, feedback2)

        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(feedbackList1, feedbackList2)

        val viewModel = FeedbackListViewModel(feedbackRepository, userSessionManager)

        advanceUntilIdle()

        assertEquals(feedbackList2, viewModel.feedbackList.value)
    }

    @Test
    fun `refreshFeedback re-loads feedback`() = runTest {
        val user = mockk<RealmUser>()
        coEvery { userSessionManager.getUserModel() } returns user

        val feedback1 = mockk<RealmFeedback>()
        val feedbackList1 = listOf(feedback1)

        val feedback2 = mockk<RealmFeedback>()
        val feedbackList2 = listOf(feedback1, feedback2)

        var emitCount = 0
        coEvery { feedbackRepository.getFeedback(user) } answers {
            emitCount++
            if (emitCount == 1) flowOf(feedbackList1) else flowOf(feedbackList2)
        }

        val viewModel = FeedbackListViewModel(feedbackRepository, userSessionManager)

        advanceUntilIdle()
        assertEquals(feedbackList1, viewModel.feedbackList.value)

        viewModel.refreshFeedback()
        advanceUntilIdle()
        assertEquals(feedbackList2, viewModel.feedbackList.value)
    }
}
