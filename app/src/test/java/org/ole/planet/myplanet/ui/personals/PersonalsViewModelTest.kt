package org.ole.planet.myplanet.ui.personals

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var personalsRepository: PersonalsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: PersonalsViewModel

    @Before
    fun setup() {
        personalsRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        viewModel = PersonalsViewModel(personalsRepository, userRepository)
    }

    @Test
    fun `personals flow emits resources for the current user id`() = runTest {
        val user = UserEntity().apply { id = "user-123" }
        val personal = Personal().apply { id = "p-1"; title = "My Personal" }
        coEvery { userRepository.getUserModel() } returns user
        coEvery { personalsRepository.getPersonalResources("user-123") } returns flowOf(listOf(personal))

        val collected = mutableListOf<List<Personal>>()
        val job = launch { viewModel.personals.collect { collected.add(it) } }
        advanceUntilIdle()

        assertTrue(collected.isNotEmpty())
        assertEquals(listOf(personal), collected.last())
        coVerify { personalsRepository.getPersonalResources("user-123") }
        job.cancel()
    }

    @Test
    fun `personals flow falls back to empty list when there is no current user`() = runTest {
        coEvery { userRepository.getUserModel() } returns null
        coEvery { personalsRepository.getPersonalResources(null) } returns flowOf(emptyList())

        val collected = mutableListOf<List<Personal>>()
        val job = launch { viewModel.personals.collect { collected.add(it) } }
        advanceUntilIdle()

        assertTrue(collected.isNotEmpty())
        assertEquals(0, collected.last().size)
        coVerify { personalsRepository.getPersonalResources(null) }
        job.cancel()
    }

    @Test
    fun `uploadPersonal transitions through Loading to Success on a successful upload`() = runTest {
        val personal = Personal().apply { id = "p-1"; title = "Title" }
        coEvery { personalsRepository.uploadPersonal(personal) } returns "uploaded-id"

        assertEquals(UploadState.Idle, viewModel.uploadState.value)

        viewModel.uploadPersonal(personal)
        advanceUntilIdle()

        assertEquals(UploadState.Success("uploaded-id"), viewModel.uploadState.value)
        coVerify { personalsRepository.uploadPersonal(personal) }
    }

    @Test
    fun `uploadPersonal transitions through Loading to Error when the repository throws`() = runTest {
        val personal = Personal().apply { id = "p-1"; title = "Title" }
        coEvery { personalsRepository.uploadPersonal(personal) } throws RuntimeException("boom")

        assertEquals(UploadState.Idle, viewModel.uploadState.value)

        viewModel.uploadPersonal(personal)
        advanceUntilIdle()

        assertEquals(UploadState.Error("boom"), viewModel.uploadState.value)
    }

    @Test
    fun `uploadPersonal surfaces a fallback message when the exception has no message`() = runTest {
        val personal = Personal().apply { id = "p-1"; title = "Title" }
        coEvery { personalsRepository.uploadPersonal(personal) } throws RuntimeException()

        viewModel.uploadPersonal(personal)
        advanceUntilIdle()

        assertEquals(UploadState.Error("Upload failed"), viewModel.uploadState.value)
    }

    @Test
    fun `uploadState starts as Idle and resetUploadState returns it to Idle`() = runTest {
        val personal = Personal().apply { id = "p-1"; title = "Title" }
        coEvery { personalsRepository.uploadPersonal(personal) } returns "uploaded-id"

        assertEquals(UploadState.Idle, viewModel.uploadState.value)

        viewModel.uploadPersonal(personal)
        advanceUntilIdle()
        assertEquals(UploadState.Success("uploaded-id"), viewModel.uploadState.value)

        viewModel.resetUploadState()
        assertEquals(UploadState.Idle, viewModel.uploadState.value)
    }
}
