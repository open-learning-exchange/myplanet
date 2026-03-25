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
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AddExaminationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: AddExaminationViewModel
    private lateinit var healthRepository: HealthRepository

    @Before
    fun setup() {
        healthRepository = mockk()
        viewModel = AddExaminationViewModel(healthRepository)
    }

    @Test
    fun saveExamination_success_emitsTrueAndResetsIsSaving() = runTest {
        val examination = mockk<RealmHealthExamination>()
        val pojo = mockk<RealmHealthExamination>()
        val user = mockk<RealmUser>()
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
        val examination = mockk<RealmHealthExamination>()
        val pojo = mockk<RealmHealthExamination>()
        val user = mockk<RealmUser>()
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
        val examination = mockk<RealmHealthExamination>()
        val pojo = mockk<RealmHealthExamination>()
        val user = mockk<RealmUser>()

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
