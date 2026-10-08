package org.ole.planet.myplanet.ui.health

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Examination
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class HealthExaminationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HealthExaminationViewModel
    private lateinit var healthRepository: HealthRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun setup() {
        healthRepository = mockk()
        userRepository = mockk()
        coEvery { userRepository.getUserModel() } returns null
        viewModel = HealthExaminationViewModel(
            healthRepository,
            userRepository,
            TestDispatcherProvider(mainDispatcherRule.testDispatcher)
        )
    }

    @Test
    fun loadData_success_updatesState() = runTest {
        val mockUser = mockk<UserEntity>()
        val mockPojo = mockk<HealthExamination>()
        val mockHealth = mockk<MyHealth>()
        val mockExamination = mockk<HealthExamination>()

        coEvery { userRepository.getUserById("user_id") } returns mockUser
        coEvery { healthRepository.getByIdOrUserId("user_id") } returns mockPojo
        coEvery { userRepository.ensureUserSecurityKeys("user_id") } returns mockUser
        coEvery { healthRepository.getDecryptedHealth(mockPojo, mockUser) } returns mockHealth
        coEvery { healthRepository.getExaminationById("exam_id") } returns mockExamination
        coEvery { healthRepository.getExaminationConditions(mockExamination) } returns mapOf("Condition A" to true)

        val states = mutableListOf<HealthExaminationState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.toList(states)
        }

        viewModel.loadData("user_id", "exam_id")
        advanceUntilIdle()

        val finalState = states.last()

        assertFalse(finalState.isLoading)
        assertEquals(mockUser, finalState.user)
        assertEquals(mockPojo, finalState.pojo)
        assertEquals(mockHealth, finalState.health)
        assertEquals(mockExamination, finalState.examination)
        assertEquals(mapOf("Condition A" to true), finalState.conditionsMap)

        job.cancel()
    }

    @Test
    fun loadData_decryptionFails_fallsBackToInitHealth() = runTest {
        val mockUser = mockk<UserEntity>()
        val mockPojo = mockk<HealthExamination>()
        val mockHealth = mockk<MyHealth>()

        coEvery { userRepository.getUserById("user_id") } returns mockUser
        coEvery { healthRepository.getByIdOrUserId("user_id") } returns mockPojo
        coEvery { userRepository.ensureUserSecurityKeys("user_id") } returns mockUser
        coEvery { healthRepository.getDecryptedHealth(mockPojo, mockUser) } returns null
        coEvery { healthRepository.initHealth() } returns mockHealth
        coEvery { healthRepository.getExaminationConditions(null) } returns emptyMap()

        val states = mutableListOf<HealthExaminationState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.toList(states)
        }

        viewModel.loadData("user_id", null)
        advanceUntilIdle()

        val finalState = states.last()

        assertFalse(finalState.isLoading)
        assertEquals(mockHealth, finalState.health)
        coVerify(exactly = 1) { healthRepository.initHealth() }

        job.cancel()
    }

    @Test
    fun saveExamination_success_emitsTrueAndResetsIsSaving() = runTest {
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = UserEntity()
        val sign = Examination()
        coEvery { healthRepository.saveExamination(examination, pojo, user) } returns Unit

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user, sign)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertTrue(results.first())
        assertFalse(viewModel.isSaving.value)

        job.cancel()
    }

    @Test
    fun saveExamination_error_emitsFalseAndResetsIsSaving() = runTest {
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = UserEntity()
        val sign = Examination()
        coEvery { healthRepository.saveExamination(examination, pojo, user) } throws RuntimeException("Network error")

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user, sign)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertFalse(results.first())
        assertFalse(viewModel.isSaving.value)

        job.cancel()
    }

    @Test
    fun saveExamination_alreadySaving_isNoOp() = runTest {
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = UserEntity()
        val sign = Examination()

        coEvery { healthRepository.saveExamination(examination, pojo, user) } coAnswers { delay(100) }

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user, sign)
        viewModel.saveExamination(examination, pojo, user, sign)

        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.saveExamination(examination, pojo, user) }
        assertEquals(1, results.size)
        assertTrue(results.first())
        assertFalse(viewModel.isSaving.value)

        job.cancel()
    }

    @Test
    fun saveExamination_nullKeys_generatesKeyAndIvAndEncryptsData() = runTest {
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = UserEntity(id = "u1").apply {
            key = null
            iv = null
        }
        val sign = Examination().apply {
            notes = "Test notes"
            diagnosis = "Test diagnosis"
        }

        coEvery { healthRepository.saveExamination(examination, pojo, user) } returns Unit

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user, sign)
        advanceUntilIdle()

        val key = user.key
        val iv = user.iv
        assertNotNull(key)
        assertNotNull(iv)

        val decryptedJson = AndroidDecrypter.decrypt(examination.data, key, iv)
        val decryptedSign = GsonUtils.gson.fromJson(decryptedJson, Examination::class.java)

        assertEquals("Test notes", decryptedSign.notes)
        assertEquals("Test diagnosis", decryptedSign.diagnosis)

        coVerify(exactly = 1) { healthRepository.saveExamination(examination, pojo, user) }

        job.cancel()
    }

    @Test
    fun saveExamination_existingKeys_reusesKeyAndIvAndEncryptsData() = runTest {
        val presetKey = AndroidDecrypter.generateKey()
        val presetIv = AndroidDecrypter.generateIv()
        val examination = HealthExamination()
        val pojo = HealthExamination()
        val user = UserEntity(id = "u1").apply {
            key = presetKey
            iv = presetIv
        }
        val sign = Examination().apply {
            notes = "Existing key notes"
            diagnosis = "Existing key diagnosis"
        }

        coEvery { healthRepository.saveExamination(examination, pojo, user) } returns Unit

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user, sign)
        advanceUntilIdle()

        assertEquals(presetKey, user.key)
        assertEquals(presetIv, user.iv)

        val decryptedJson = AndroidDecrypter.decrypt(examination.data, presetKey, presetIv)
        val decryptedSign = GsonUtils.gson.fromJson(decryptedJson, Examination::class.java)

        assertEquals("Existing key notes", decryptedSign.notes)
        assertEquals("Existing key diagnosis", decryptedSign.diagnosis)

        coVerify(exactly = 1) { healthRepository.saveExamination(examination, pojo, user) }

        job.cancel()
    }

    @Test
    fun loadData_populatesCurrentUserAsExaminer_andUserAsPatient() = runTest {
        val patientUser = mockk<UserEntity>()
        val examinerUser = mockk<UserEntity>()
        val mockPojo = mockk<HealthExamination>()
        val mockHealth = mockk<MyHealth>()

        coEvery { userRepository.getUserById("patient_id") } returns patientUser
        coEvery { healthRepository.getByIdOrUserId("patient_id") } returns mockPojo
        coEvery { userRepository.ensureUserSecurityKeys("patient_id") } returns patientUser
        coEvery { userRepository.getUserModel() } returns examinerUser
        coEvery { healthRepository.getDecryptedHealth(mockPojo, patientUser) } returns mockHealth
        coEvery { healthRepository.getExaminationConditions(null) } returns emptyMap()

        val states = mutableListOf<HealthExaminationState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.toList(states)
        }

        viewModel.loadData("patient_id", null)
        advanceUntilIdle()

        val finalState = states.last()

        assertFalse(finalState.isLoading)
        assertEquals(patientUser, finalState.user)
        assertEquals(examinerUser, finalState.currentUser)

        job.cancel()
    }

    @Test
    fun loadData_callsGetByIdOrUserIdOnce_andNeverCallsGetExaminationByIdForUser() = runTest {
        val mockUser = mockk<UserEntity>()
        val mockPojo = mockk<HealthExamination>()
        val mockHealth = mockk<MyHealth>()

        coEvery { userRepository.getUserById("patient_id") } returns mockUser
        coEvery { healthRepository.getByIdOrUserId("patient_id") } returns mockPojo
        coEvery { userRepository.ensureUserSecurityKeys("patient_id") } returns mockUser
        coEvery { healthRepository.getDecryptedHealth(mockPojo, mockUser) } returns mockHealth
        coEvery { healthRepository.getExaminationConditions(null) } returns emptyMap()

        viewModel.loadData("patient_id", null)
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.getByIdOrUserId("patient_id") }
        coVerify(exactly = 0) { healthRepository.getExaminationById("patient_id") }
    }
}
