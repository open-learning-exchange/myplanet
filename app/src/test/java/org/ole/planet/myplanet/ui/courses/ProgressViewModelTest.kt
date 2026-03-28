package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonArray
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: ProgressViewModel
    private val progressRepository: ProgressRepository = mockk()
    private val userSessionManager: UserSessionManager = mockk()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        viewModel = ProgressViewModel(progressRepository, userSessionManager, dispatcherProvider)
    }

    @Test
    fun loadCourseData_updatesCourseData() = runTest {
        val user = RealmUser().apply { id = "user_123" }
        coEvery { userSessionManager.getUserModel() } returns user

        val expectedJsonArray = JsonArray().apply { add("course1") }
        coEvery { progressRepository.fetchCourseData(user.id) } returns expectedJsonArray

        assertNull(viewModel.courseData.value)

        viewModel.loadCourseData()

        advanceUntilIdle()

        io.mockk.coVerify { progressRepository.fetchCourseData("user_123") }

        assertEquals(expectedJsonArray, viewModel.courseData.value)
    }
}
