package org.ole.planet.myplanet.ui.health

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val record = HealthRecord(
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
    fun `selectPatient called repeatedly for same patient returns early and does not re-query`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        coEvery { healthRepository.getPatientById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        viewModel.selectPatient("1")
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.getPatientById("1") }
    }

    @Test
    fun `refreshSelectedPatient forces re-query for current patient`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        coEvery { healthRepository.getPatientById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        viewModel.refreshSelectedPatient()
        advanceUntilIdle()

        coVerify(exactly = 2) { healthRepository.getPatientById("1") }
    }

    @Test
    fun `selectPatient with different patient ID loads new patient`() = runTest {
        val user1 = UserEntity().apply { id = "1"; name = "Test Patient 1" }
        val user2 = UserEntity().apply { id = "2"; name = "Test Patient 2" }
        val record1 = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        val record2 = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { healthRepository.getPatientById("1") } returns user1
        coEvery { healthRepository.getPatientHealthRecords("1", user1) } returns record1
        coEvery { healthRepository.getPatientById("2") } returns user2
        coEvery { healthRepository.getPatientHealthRecords("2", user2) } returns record2

        viewModel.selectPatient("1")
        advanceUntilIdle()

        viewModel.selectPatient("2")
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.getPatientById("1") }
        coVerify(exactly = 1) { healthRepository.getPatientById("2") }

        val state = viewModel.patientDetailState.first()
        assertEquals(user2, state.user)
    }

    @Test
    fun `selectPatient resets tracked ID on failure so retry works`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { healthRepository.getPatientById("1") } returns null

        viewModel.selectPatient("1")
        advanceUntilIdle()

        assertNull(viewModel.patientDetailState.first().user)

        coEvery { healthRepository.getPatientById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        coVerify(exactly = 2) { healthRepository.getPatientById("1") }
        assertEquals(user, viewModel.patientDetailState.first().user)
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
