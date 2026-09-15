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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
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

        coEvery { healthRepository.getHealthEntry("user_id") } returns Pair(mockUser, mockPojo)
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

        coEvery { healthRepository.getHealthEntry("user_id") } returns Pair(mockUser, mockPojo)
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
        val examination = mockk<HealthExamination>()
        val pojo = mockk<HealthExamination>()
        val user = mockk<UserEntity>()
        coEvery { healthRepository.saveExamination(examination, pojo, user) } returns Unit

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertTrue(results.first())
        assertFalse(viewModel.isSaving.value)

        job.cancel()
    }

    @Test
    fun saveExamination_error_emitsFalseAndResetsIsSaving() = runTest {
        val examination = mockk<HealthExamination>()
        val pojo = mockk<HealthExamination>()
        val user = mockk<UserEntity>()
        coEvery { healthRepository.saveExamination(examination, pojo, user) } throws RuntimeException("Network error")

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertFalse(results.first())
        assertFalse(viewModel.isSaving.value)

        job.cancel()
    }

    @Test
    fun saveExamination_alreadySaving_isNoOp() = runTest {
        val examination = mockk<HealthExamination>()
        val pojo = mockk<HealthExamination>()
        val user = mockk<UserEntity>()

        coEvery { healthRepository.saveExamination(examination, pojo, user) } coAnswers { delay(100) }

        val results = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveResult.toList(results)
        }

        viewModel.saveExamination(examination, pojo, user)
        viewModel.saveExamination(examination, pojo, user)

        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.saveExamination(examination, pojo, user) }
        assertEquals(1, results.size)
        assertTrue(results.first())
        assertFalse(viewModel.isSaving.value)

        job.cancel()
    }
}
