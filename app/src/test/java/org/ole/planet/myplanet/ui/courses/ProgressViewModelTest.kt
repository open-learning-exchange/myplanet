package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonArray
import io.mockk.coEvery
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var viewModel: ProgressViewModel
    private val progressRepository: ProgressRepository = mockk()
    private val userSessionManager: UserSessionManager = mockk()

    @Before
    fun setUp() {
        viewModel = ProgressViewModel(progressRepository, userSessionManager)
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

        assertEquals(expectedJsonArray, viewModel.courseData.value)
    }
}
