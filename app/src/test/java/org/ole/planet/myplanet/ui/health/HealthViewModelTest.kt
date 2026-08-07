package org.ole.planet.myplanet.ui.health

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
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private lateinit var userRepository: UserRepository
    private lateinit var healthRepository: HealthRepository
    private lateinit var viewModel: HealthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()
        healthRepository = mockk()
        viewModel = HealthViewModel(userRepository, healthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPatients updates patientList`() = runTest {
        val patients = listOf(UserEntity().apply { id = "1"; name = "Test Patient" })
        coEvery { healthRepository.getPatientsSortedBy("joinDate", true) } returns patients

        viewModel.loadPatients()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(patients, viewModel.patientList.first())
    }
}
