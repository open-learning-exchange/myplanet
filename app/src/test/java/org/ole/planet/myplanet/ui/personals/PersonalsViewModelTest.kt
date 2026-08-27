package org.ole.planet.myplanet.ui.personals

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalsViewModelTest {

    private val personalsRepository: PersonalsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `personals emits resources scoped to the current user from UserRepository`() = runTest(testDispatcher) {
        val user = UserEntity().apply { id = "user-1" }
        coEvery { userRepository.getUserModel() } returns user
        val resources = listOf(Personal().apply { id = "p1" }, Personal().apply { id = "p2" })
        coEvery { personalsRepository.getPersonalResources("user-1") } returns flowOf(resources)

        val viewModel = PersonalsViewModel(personalsRepository, userRepository)

        val emissions = mutableListOf<List<Personal>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.personals.toList(emissions)
        }
        advanceUntilIdle()

        assertEquals(resources, emissions.last())
        coVerify(exactly = 1) { personalsRepository.getPersonalResources("user-1") }
        job.cancel()
    }

    @Test
    fun `personals passes null userId when UserRepository has no current user`() = runTest(testDispatcher) {
        coEvery { userRepository.getUserModel() } returns null
        coEvery { personalsRepository.getPersonalResources(null) } returns flowOf(emptyList())

        val viewModel = PersonalsViewModel(personalsRepository, userRepository)

        val emissions = mutableListOf<List<Personal>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.personals.toList(emissions)
        }
        advanceUntilIdle()

        assertEquals(emptyList<Personal>(), emissions.last())
        coVerify(exactly = 1) { personalsRepository.getPersonalResources(null) }
        job.cancel()
    }
}
