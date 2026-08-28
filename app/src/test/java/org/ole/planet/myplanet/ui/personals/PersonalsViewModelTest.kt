package org.ole.planet.myplanet.ui.personals

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.collectEmissions

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val personalsRepository: PersonalsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()

    @Test
    fun `personals emits resources scoped to the current user from UserRepository`() = runTest(testDispatcher) {
        val user = UserEntity().apply { id = "user-1" }
        coEvery { userRepository.getUserModel() } returns user
        val resources = listOf(Personal().apply { id = "p1" }, Personal().apply { id = "p2" })
        coEvery { personalsRepository.getPersonalResources("user-1") } returns flowOf(resources)

        val viewModel = PersonalsViewModel(personalsRepository, userRepository)

        val emissions = collectEmissions(viewModel.personals)

        assertEquals(resources, emissions.last())
        coVerify(exactly = 1) { personalsRepository.getPersonalResources("user-1") }
    }

    @Test
    fun `personals passes null userId when UserRepository has no current user`() = runTest(testDispatcher) {
        coEvery { userRepository.getUserModel() } returns null
        coEvery { personalsRepository.getPersonalResources(null) } returns flowOf(emptyList())

        val viewModel = PersonalsViewModel(personalsRepository, userRepository)

        val emissions = collectEmissions(viewModel.personals)

        assertEquals(emptyList<Personal>(), emissions.last())
        coVerify(exactly = 1) { personalsRepository.getPersonalResources(null) }
    }
}
