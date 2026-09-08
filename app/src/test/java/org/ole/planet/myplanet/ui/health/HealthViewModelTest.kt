package org.ole.planet.myplanet.ui.health

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private lateinit var userRepository: UserRepository
    private lateinit var healthRepository: HealthRepository
    private lateinit var viewModel: HealthViewModel
    @get:org.junit.Rule
    val mainDispatcherRule = MainDispatcherRule()

    @org.junit.Before
    fun setup() {
        userRepository = mockk()
        healthRepository = mockk()
        viewModel = HealthViewModel(userRepository, healthRepository)
    }

    @Test
    fun `selectPatient updates patientDetailState`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = org.ole.planet.myplanet.model.HealthRecord(
            mockk(), mockk(), emptyList(), emptyMap()
        )
        coEvery { healthRepository.getPatientById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        val state = viewModel.patientDetailState.first()
        assertEquals(user, state.user)
        assertEquals(record, state.healthRecord)
        assertEquals(false, viewModel.isLoading.first())
    }

    @Test
    fun `searchPatients updates patientList`() = runTest {
        val query = "John"
        val patients = listOf(UserEntity().apply { id = "2"; name = "John Doe" })
        coEvery { healthRepository.searchPatients(query, "joinDate", true) } returns patients

        viewModel.searchPatients(query)
        advanceUntilIdle()

        assertEquals(patients, viewModel.patientList.first())
        assertEquals(false, viewModel.isListLoading.first())
    }

    @Test
    fun `loadPatients updates patientList`() = runTest {
        val patients = listOf(UserEntity().apply { id = "1"; name = "Test Patient" })
        coEvery { healthRepository.getPatientsSortedBy("joinDate", true) } returns patients

        viewModel.loadPatients()
        advanceUntilIdle()

        assertEquals(patients, viewModel.patientList.first())
    }
}
