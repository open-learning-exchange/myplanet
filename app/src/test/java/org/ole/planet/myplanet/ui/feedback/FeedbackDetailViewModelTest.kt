package org.ole.planet.myplanet.ui.feedback

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackDetailViewModelTest {

    private lateinit var viewModel: FeedbackDetailViewModel
    private lateinit var feedbackRepository: FeedbackRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        feedbackRepository = mockk()
        viewModel = FeedbackDetailViewModel(feedbackRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadFeedback() = runTest(testDispatcher) {
        val feedbackId = "123"
        val mockFeedback = mockk<RealmFeedback>()
        coEvery { feedbackRepository.getFeedbackById(feedbackId) } returns mockFeedback

        viewModel.loadFeedback(feedbackId)
        advanceUntilIdle()

        assertEquals(mockFeedback, viewModel.feedback.value)
        coVerify(exactly = 1) { feedbackRepository.getFeedbackById(feedbackId) }
    }

    @Test
    fun testAddReply() = runTest(testDispatcher) {
        val feedbackId = "123"
        val mockMessage = "Test message"
        val mockUser = "testuser"
        val mockFeedback = mockk<RealmFeedback>()
        coEvery { feedbackRepository.addReply(feedbackId, mockMessage, mockUser) } returns Unit
        coEvery { feedbackRepository.getFeedbackById(feedbackId) } returns mockFeedback

        viewModel.addReply(feedbackId, mockMessage, mockUser)
        advanceUntilIdle()

        assertEquals(mockFeedback, viewModel.feedback.value)
        coVerify(exactly = 1) { feedbackRepository.addReply(feedbackId, mockMessage, mockUser) }
        coVerify(exactly = 1) { feedbackRepository.getFeedbackById(feedbackId) }
    }

    @Test
    fun testCloseFeedback() = runTest(testDispatcher) {
        val feedbackId = "123"
        val mockFeedback = mockk<RealmFeedback>()
        coEvery { feedbackRepository.closeFeedback(feedbackId) } returns Unit
        coEvery { feedbackRepository.getFeedbackById(feedbackId) } returns mockFeedback

        val eventsList = mutableListOf<FeedbackDetailViewModel.FeedbackDetailEvent>()
        val job = launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.events.toList(eventsList)
        }

        viewModel.closeFeedback(feedbackId)
        advanceUntilIdle()

        assertEquals(mockFeedback, viewModel.feedback.value)
        assertEquals(1, eventsList.size)
        assertEquals(FeedbackDetailViewModel.FeedbackDetailEvent.CloseFeedbackSuccess, eventsList.first())
        coVerify(exactly = 1) { feedbackRepository.closeFeedback(feedbackId) }
        coVerify(exactly = 1) { feedbackRepository.getFeedbackById(feedbackId) }

        job.cancel()
    }
}
