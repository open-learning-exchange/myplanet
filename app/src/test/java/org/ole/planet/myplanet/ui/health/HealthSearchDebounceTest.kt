package org.ole.planet.myplanet.ui.health

import android.app.Application
import android.content.Context
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.textChanges
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HealthSearchDebounceTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var healthRepository: HealthRepository
    private lateinit var viewModel: HealthViewModel

    @get:org.junit.Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Before
    fun setup() {
        userRepository = mockk()
        healthRepository = mockk()
        viewModel = HealthViewModel(userRepository, healthRepository)
        coEvery { healthRepository.searchPatients(any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `a burst of keystrokes fires a single DAO query`() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val etSearch = EditText(context)
        val patients = listOf(UserEntity().apply { id = "1"; name = "John Doe" })
        coEvery { healthRepository.searchPatients("John", "joinDate", true) } returns patients

        val searchJob = etSearch.textChanges()
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query -> viewModel.searchPatients(query?.toString() ?: "", "joinDate", true) }
            .launchIn(this)

        for (fragment in listOf("J", "Jo", "Joh", "John")) {
            etSearch.setText(fragment)
            runCurrent()
        }

        coVerify(exactly = 0) { healthRepository.searchPatients(any(), any(), any()) }

        advanceTimeBy(300)
        runCurrent()
        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.searchPatients("John", "joinDate", true) }
        assertEquals(patients, viewModel.patientList.value)

        searchJob.cancel()
        runCurrent()
    }
}
