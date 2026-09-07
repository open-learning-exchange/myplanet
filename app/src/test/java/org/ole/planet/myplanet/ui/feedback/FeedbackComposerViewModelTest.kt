package org.ole.planet.myplanet.ui.feedback

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackComposerViewModelTest {

    private lateinit var viewModel: FeedbackComposerViewModel
    private val feedbackRepository: FeedbackRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FeedbackComposerViewModel(feedbackRepository, userRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitFeedback success emits Saved event`() = runTest {
        val userEntity = mockk<UserEntity>(relaxed = true)
        coEvery { userEntity.name } returns "test_user"
        coEvery { userRepository.getUserModel() } returns userEntity
        coEvery {
            feedbackRepository.createAndSaveFeedback("test_user", "yes", "bug", "message", null, null)
        } returns Unit

        viewModel.submitFeedback("yes", "bug", "message", null, null)

        val event = viewModel.events.first()
        assertTrue(event is FeedbackComposerViewModel.SubmitEvent.Saved)
    }

    @Test
    fun `submitFeedback error emits Error event`() = runTest {
        val userEntity = mockk<UserEntity>(relaxed = true)
        coEvery { userEntity.name } returns "test_user"
        coEvery { userRepository.getUserModel() } returns userEntity
        val exception = RuntimeException("Test error")
        coEvery {
            feedbackRepository.createAndSaveFeedback("test_user", "yes", "bug", "message", null, null)
        } throws exception

        viewModel.submitFeedback("yes", "bug", "message", null, null)

        val event = viewModel.events.first()
        assertTrue(event is FeedbackComposerViewModel.SubmitEvent.Error)
        assertEquals("Test error", (event as FeedbackComposerViewModel.SubmitEvent.Error).message)
    }
}
