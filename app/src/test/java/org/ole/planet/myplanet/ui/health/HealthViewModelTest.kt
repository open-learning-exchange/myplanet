package org.ole.planet.myplanet.ui.health

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
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
        viewModel = HealthViewModel(userRepository, healthRepository, RealtimeSyncManager())
    }

    @Test
    fun `selectPatient updates patientDetailState`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(
            mockk(), mockk(), emptyList(), emptyMap()
        )
        coEvery { userRepository.getUserById("1") } returns user
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
        coEvery { userRepository.getUserById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        viewModel.selectPatient("1")
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getUserById("1") }
    }

    @Test
    fun `selectPatient superseding load keeps isLoading true while second load is in flight`() = runTest {
        val user1 = UserEntity().apply { id = "1"; name = "Patient 1" }
        val user2 = UserEntity().apply { id = "2"; name = "Patient 2" }
        val record2 = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserById("1") } coAnswers {
            delay(1000)
            user1
        }
        coEvery { userRepository.getUserById("2") } coAnswers {
            delay(1000)
            user2
        }
        coEvery { healthRepository.getPatientHealthRecords("2", user2) } returns record2

        viewModel.selectPatient("1")
        testScheduler.advanceTimeBy(100)
        assertEquals(true, viewModel.isLoading.value)

        viewModel.selectPatient("2")
        testScheduler.advanceTimeBy(100)

        assertEquals(true, viewModel.isLoading.value)

        advanceUntilIdle()
        assertEquals(false, viewModel.isLoading.value)
        assertEquals(user2, viewModel.patientDetailState.first().user)
    }

    @Test
    fun `refreshSelectedPatient on cold start falls back to loadInitialPatient`() = runTest {
        val currentUser = UserEntity().apply { id = "user1"; name = "Logged In User" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { userRepository.getUserById("user1") } returns currentUser
        coEvery { healthRepository.getPatientHealthRecords("user1", currentUser) } returns record

        viewModel.refreshSelectedPatient()
        advanceUntilIdle()

        assertEquals(currentUser, viewModel.patientDetailState.first().user)
    }

    @Test
    fun `refreshSelectedPatient forces re-query for current patient`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        coEvery { userRepository.getUserById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        viewModel.refreshSelectedPatient()
        advanceUntilIdle()

        coVerify(exactly = 2) { userRepository.getUserById("1") }
    }

    @Test
    fun `selectPatient with different patient ID loads new patient`() = runTest {
        val user1 = UserEntity().apply { id = "1"; name = "Test Patient 1" }
        val user2 = UserEntity().apply { id = "2"; name = "Test Patient 2" }
        val record1 = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        val record2 = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserById("1") } returns user1
        coEvery { healthRepository.getPatientHealthRecords("1", user1) } returns record1
        coEvery { userRepository.getUserById("2") } returns user2
        coEvery { healthRepository.getPatientHealthRecords("2", user2) } returns record2

        viewModel.selectPatient("1")
        advanceUntilIdle()

        viewModel.selectPatient("2")
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getUserById("1") }
        coVerify(exactly = 1) { userRepository.getUserById("2") }

        val state = viewModel.patientDetailState.first()
        assertEquals(user2, state.user)
    }

    @Test
    fun `selectPatient handles non-cancellation exception gracefully leaving state empty`() = runTest {
        coEvery { userRepository.getUserById("1") } throws RuntimeException("Database error")

        viewModel.selectPatient("1")
        advanceUntilIdle()

        val state = viewModel.patientDetailState.first()
        assertNull(state.user)
        assertNull(state.healthRecord)
        assertEquals(false, viewModel.isLoading.first())
    }

    @Test
    fun `selectPatient resets tracked ID on failure so retry works`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserById("1") } returns null

        viewModel.selectPatient("1")
        advanceUntilIdle()

        assertNull(viewModel.patientDetailState.first().user)

        coEvery { userRepository.getUserById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        coVerify(exactly = 2) { userRepository.getUserById("1") }
        assertEquals(user, viewModel.patientDetailState.first().user)
    }

    @Test
    fun `searchPatients updates patientList`() = runTest {
        val query = "John"
        val patients = listOf(UserEntity().apply { id = "2"; name = "John Doe" })
        coEvery { userRepository.searchUsers(query, "joinDate", true) } returns patients

        viewModel.searchPatients(query)
        advanceUntilIdle()

        assertEquals(patients, viewModel.patientList.first())
        assertEquals(false, viewModel.isListLoading.first())
    }

    @Test
    fun `searchPatients with whitespace query calls getUsersSortedBy once and searchUsers zero times`() = runTest {
        val patients = listOf(UserEntity().apply { id = "1"; name = "Test User" })
        coEvery { userRepository.getUsersSortedBy("joinDate", true) } returns patients

        viewModel.searchPatients("   ")
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getUsersSortedBy("joinDate", true) }
        coVerify(exactly = 0) { userRepository.searchUsers(any(), any(), any()) }
        assertEquals(patients, viewModel.patientList.first())
    }

    @Test
    fun `searchPatients with non-blank query calls searchUsers once and getUsersSortedBy zero times`() = runTest {
        val query = "John"
        val patients = listOf(UserEntity().apply { id = "2"; name = "John Doe" })
        coEvery { userRepository.searchUsers("John", "joinDate", true) } returns patients

        viewModel.searchPatients("John")
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.searchUsers("John", "joinDate", true) }
        coVerify(exactly = 0) { userRepository.getUsersSortedBy(any(), any()) }
        assertEquals(patients, viewModel.patientList.first())
    }

    @Test
    fun `loadPatients updates patientList`() = runTest {
        val patients = listOf(UserEntity().apply { id = "1"; name = "Test Patient" })
        coEvery { userRepository.getUsersSortedBy("joinDate", true) } returns patients

        viewModel.loadPatients()
        advanceUntilIdle()

        assertEquals(patients, viewModel.patientList.first())
    }

    @Test
    fun `failing refresh leaves currentPatientId and displayed patient intact`() = runTest {
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        coEvery { userRepository.getUserById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        assertEquals(user, viewModel.patientDetailState.first().user)

        coEvery { healthRepository.getPatientHealthRecords("1", user) } throws RuntimeException("Transient network error")

        viewModel.refreshSelectedPatient()
        advanceUntilIdle()

        assertEquals(user, viewModel.patientDetailState.first().user)
        assertEquals(record, viewModel.patientDetailState.first().healthRecord)

        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record
        viewModel.refreshSelectedPatient()
        advanceUntilIdle()

        coVerify(exactly = 3) { userRepository.getUserById("1") }
    }

    @Test
    fun `failed load for a different patient clears the displayed one`() = runTest {
        val displayed = UserEntity().apply { id = "1"; name = "Displayed Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())
        coEvery { userRepository.getUserById("1") } returns displayed
        coEvery { healthRepository.getPatientHealthRecords("1", displayed) } returns record

        viewModel.selectPatient("1")
        advanceUntilIdle()

        assertEquals(displayed, viewModel.patientDetailState.first().user)

        coEvery { userRepository.getUserById("2") } throws RuntimeException("Transient network error")

        viewModel.selectPatient("2")
        advanceUntilIdle()

        assertNull(viewModel.patientDetailState.first().user)
        assertNull(viewModel.patientDetailState.first().healthRecord)
    }

    @Test
    fun `health sync event restarts an in-flight load so the newest read wins`() = runTest {
        val realtimeSyncManager = RealtimeSyncManager()
        val customViewModel = HealthViewModel(userRepository, healthRepository, realtimeSyncManager)
        val staleUser = UserEntity().apply { id = "1"; name = "Stale Patient" }
        val freshUser = UserEntity().apply { id = "1"; name = "Fresh Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        var reads = 0
        coEvery { userRepository.getUserById("1") } coAnswers {
            reads++
            delay(500)
            if (reads == 1) staleUser else freshUser
        }
        coEvery { healthRepository.getPatientHealthRecords("1", any()) } returns record

        customViewModel.setSyncActive(true)
        testScheduler.runCurrent()

        customViewModel.selectPatient("1")
        testScheduler.advanceTimeBy(100)

        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("health", 1, 0, true))

        advanceUntilIdle()

        coVerify(exactly = 2) { userRepository.getUserById("1") }
        assertEquals(freshUser, customViewModel.patientDetailState.first().user)
        assertEquals(false, customViewModel.isLoading.value)
    }

    @Test
    fun `two rapid health-table events coalesce into a single refresh`() = runTest {
        val realtimeSyncManager = RealtimeSyncManager()
        val customViewModel = HealthViewModel(userRepository, healthRepository, realtimeSyncManager)
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserById("1") } coAnswers {
            delay(500)
            user
        }
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        customViewModel.setSyncActive(true)
        testScheduler.runCurrent()

        customViewModel.selectPatient("1")
        testScheduler.advanceTimeBy(100)

        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("health", 1, 0, true))
        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("health", 1, 0, true))

        advanceUntilIdle()

        coVerify(exactly = 2) { userRepository.getUserById("1") }
        assertEquals(user, customViewModel.patientDetailState.first().user)
    }

    @Test
    fun `health events are ignored while nothing collects the sync flow`() = runTest {
        val realtimeSyncManager = RealtimeSyncManager()
        val customViewModel = HealthViewModel(userRepository, healthRepository, realtimeSyncManager)
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        customViewModel.selectPatient("1")
        advanceUntilIdle()

        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("health", 1, 0, true))
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getUserById("1") }
    }

    @Test
    fun `after setSyncActive(true) then setSyncActive(false), a health event causes no re-query`() = runTest {
        val realtimeSyncManager = RealtimeSyncManager()
        val customViewModel = HealthViewModel(userRepository, healthRepository, realtimeSyncManager)
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { healthRepository.getPatientById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        customViewModel.setSyncActive(true)
        testScheduler.runCurrent()

        customViewModel.selectPatient("1")
        advanceUntilIdle()

        customViewModel.setSyncActive(false)
        testScheduler.runCurrent()

        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("health", 1, 0, true))
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.getPatientById("1") }
    }

    @Test
    fun `sync flow ignores other tables and updates that do not require a refresh`() = runTest {
        val realtimeSyncManager = RealtimeSyncManager()
        val customViewModel = HealthViewModel(userRepository, healthRepository, realtimeSyncManager)
        val user = UserEntity().apply { id = "1"; name = "Test Patient" }
        val record = HealthRecord(mockk(), mockk(), emptyList(), emptyMap())

        coEvery { userRepository.getUserById("1") } returns user
        coEvery { healthRepository.getPatientHealthRecords("1", user) } returns record

        customViewModel.setSyncActive(true)
        testScheduler.runCurrent()

        customViewModel.selectPatient("1")
        advanceUntilIdle()

        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("courses", 1, 0, true))
        realtimeSyncManager.notifyTableUpdated(TableDataUpdate("health", 1, 0, false))
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.getUserById("1") }
    }

    @Test
    fun `saveHealthData sets isSaved on success`() = runTest {
        coEvery { healthRepository.updateUserHealthProfile("1", any()) } returns Unit

        viewModel.saveHealthData("1", emptyMap())
        advanceUntilIdle()

        assertTrue(viewModel.isSaved.value)
    }

    @Test
    fun `saveHealthData emits saveFailed and leaves isSaved false when the repository throws`() = runTest {
        coEvery { healthRepository.updateUserHealthProfile("1", any()) } throws IllegalStateException("boom")
        var failures = 0
        val job = launch(mainDispatcherRule.testDispatcher) { viewModel.saveFailed.collect { failures++ } }

        viewModel.saveHealthData("1", emptyMap())
        advanceUntilIdle()

        assertEquals(1, failures)
        assertFalse(viewModel.isSaved.value)
        job.cancel()
    }
}
