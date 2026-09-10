package org.ole.planet.myplanet.ui.exam

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class UserInformationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var userRepository: UserRepository
    private lateinit var submissionsRepository: SubmissionsRepository
    private lateinit var viewModel: UserInformationViewModel

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        submissionsRepository = mockk(relaxed = true)
        viewModel = UserInformationViewModel(userRepository, submissionsRepository)
    }

    @Test
    fun `updateProfile success emits UpdateProfileSuccess`() = runTest {
        val userId = "user123"
        val jsonUser = JsonObject()
        coEvery { userRepository.updateProfileFields(userId, jsonUser) } returns Unit

        viewModel.updateProfile(userId, jsonUser)

        val result = viewModel.resultEvent.first()
        assertEquals(UserInformationResult.UpdateProfileSuccess, result)
        coVerify(exactly = 1) { userRepository.updateProfileFields(userId, jsonUser) }
    }

    @Test
    fun `updateProfile exception emits UpdateProfileError`() = runTest {
        val userId = "user123"
        val jsonUser = JsonObject()
        val errorMessage = "Database error"
        coEvery { userRepository.updateProfileFields(userId, jsonUser) } throws Exception(errorMessage)

        viewModel.updateProfile(userId, jsonUser)

        val result = viewModel.resultEvent.first()
        assertTrue(result is UserInformationResult.UpdateProfileError)
        assertEquals(errorMessage, (result as UserInformationResult.UpdateProfileError).message)
    }

    @Test
    fun `markSubmissionComplete with null or empty submissionId emits MarkSubmissionError`() = runTest {
        val jsonUser = JsonObject()

        viewModel.markSubmissionComplete(null, jsonUser)

        val result = viewModel.resultEvent.first()
        assertTrue(result is UserInformationResult.MarkSubmissionError)
        assertEquals("no ID provided", (result as UserInformationResult.MarkSubmissionError).message)
        coVerify(exactly = 0) { submissionsRepository.markSubmissionComplete(any(), any()) }
    }

    @Test
    fun `markSubmissionComplete success emits MarkSubmissionSuccess`() = runTest {
        val submissionId = "sub123"
        val jsonUser = JsonObject()
        coEvery { submissionsRepository.markSubmissionComplete(submissionId, jsonUser) } returns Unit

        viewModel.markSubmissionComplete(submissionId, jsonUser)

        val result = viewModel.resultEvent.first()
        assertEquals(UserInformationResult.MarkSubmissionSuccess, result)
        coVerify(exactly = 1) { submissionsRepository.markSubmissionComplete(submissionId, jsonUser) }
    }

    @Test
    fun `markSubmissionComplete exception emits MarkSubmissionError`() = runTest {
        val submissionId = "sub123"
        val jsonUser = JsonObject()
        val errorMessage = "Failed to mark complete"
        coEvery { submissionsRepository.markSubmissionComplete(submissionId, jsonUser) } throws Exception(errorMessage)

        viewModel.markSubmissionComplete(submissionId, jsonUser)

        val result = viewModel.resultEvent.first()
        assertTrue(result is UserInformationResult.MarkSubmissionError)
        assertEquals(errorMessage, (result as UserInformationResult.MarkSubmissionError).message)
    }
}
