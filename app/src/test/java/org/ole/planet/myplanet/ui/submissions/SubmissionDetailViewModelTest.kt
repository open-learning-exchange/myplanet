package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.model.QuestionAnswer
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var submissionsRepository: SubmissionsRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        submissionsRepository = mock(SubmissionsRepository::class.java)
        userRepository = mock(UserRepository::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiStateDefaultsWhenSubmissionNotFound() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("id" to "sub1"))
        `when`(submissionsRepository.getSubmissionByRemoteIdOrParentId("sub1")).thenReturn(null)

        val viewModel = SubmissionDetailViewModel(submissionsRepository, userRepository, savedStateHandle)

        val job = launch {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Submission Details", state.title)
        assertEquals("Status: Unknown", state.status)
        assertEquals("Date: Unknown", state.date)
        assertEquals("Submitted by: Unknown", state.submittedBy)
        assertEquals(emptyList<QuestionAnswer>(), state.questionAnswers)

        job.cancel()
    }

    @Test
    fun testUiStateMappedWhenSubmissionFound() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("id" to "sub1"))
        val submission = Submission().apply {
            id = "sub1"
            userId = "user1"
        }
        val user = UserEntity(id = "user1", name = "Test User")
        val qAnswers = listOf(
            QuestionAnswer(
                questionId = "q1",
                questionHeader = "Q1",
                questionBody = "What is 1+1?",
                questionType = "text",
                answer = "2",
                answerChoices = null,
                isCorrect = true
            )
        )
        val detail = SubmissionDetail(
            title = "Math Quiz",
            status = "Status: Complete",
            date = 1600000000000L,
            submittedBy = "Submitted by: Test User",
            questionAnswers = qAnswers
        )

        `when`(submissionsRepository.getSubmissionByRemoteIdOrParentId("sub1")).thenReturn(submission)
        `when`(userRepository.getUserById("user1")).thenReturn(user)
        `when`(submissionsRepository.getSubmissionDetail(submission, user)).thenReturn(detail)

        val viewModel = SubmissionDetailViewModel(submissionsRepository, userRepository, savedStateHandle)

        val job = launch {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Math Quiz", state.title)
        assertEquals("Status: Complete", state.status)
        assertEquals("Submitted by: Test User", state.submittedBy)
        assertEquals(qAnswers, state.questionAnswers)

        job.cancel()
    }
}
