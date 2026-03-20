package org.ole.planet.myplanet.ui.feedback

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Test
    fun `refreshFeedback cancels previous job`() = runTest {
        val user = mockk<RealmUser>()
        coEvery { userSessionManager.getUserModel() } returns user

        val feedback1 = mockk<RealmFeedback>()
        val feedbackList1 = listOf(feedback1)
        val feedbackList2 = listOf(feedback1, feedback1)

        // Make the flow never complete normally so we can observe cancellation
        coEvery { feedbackRepository.getFeedback(user) } returns flow {
            emit(feedbackList1)
            kotlinx.coroutines.delay(1000)
            emit(feedbackList2)
        }

        val viewModel = FeedbackListViewModel(feedbackRepository, userSessionManager)

        // Give it just enough time to collect the first item but not finish the delay
        testScheduler.advanceTimeBy(500)
        assertEquals(feedbackList1, viewModel.feedbackList.value)

        // Mock second invocation to complete quickly
        coEvery { feedbackRepository.getFeedback(user) } returns flowOf(emptyList())

        // Calling refresh should cancel the previous flow
        viewModel.refreshFeedback()
        advanceUntilIdle()

        // The second collect emits emptyList. The first one was cancelled during its delay.
        assertEquals(emptyList<RealmFeedback>(), viewModel.feedbackList.value)
        coVerify(exactly = 2) { feedbackRepository.getFeedback(user) }
    }

    @Test
    fun `loadFeedback handles null user`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns null

        val feedback1 = mockk<RealmFeedback>()
        val feedbackList1 = listOf(feedback1)

        coEvery { feedbackRepository.getFeedback(null) } returns flowOf(feedbackList1)

        val viewModel = FeedbackListViewModel(feedbackRepository, userSessionManager)

        advanceUntilIdle()

        assertEquals(feedbackList1, viewModel.feedbackList.value)
    }

    @Test
    fun `loadFeedback catches exception from repository`() = runTest {
        val user = mockk<RealmUser>()
        coEvery { userSessionManager.getUserModel() } returns user

        coEvery { feedbackRepository.getFeedback(user) } throws RuntimeException("Network error")

        val viewModel = FeedbackListViewModel(feedbackRepository, userSessionManager)

        advanceUntilIdle()

        // List should remain empty, exception should be caught and printStackTrace called
        assertEquals(emptyList<RealmFeedback>(), viewModel.feedbackList.value)
    }
}
