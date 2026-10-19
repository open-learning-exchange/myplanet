package org.ole.planet.myplanet.ui.courses

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: ProgressViewModel
    private val progressRepository: ProgressRepository = mockk()
    private val userRepository: UserRepository = mockk()

    @Before
    fun setUp() {
        viewModel = ProgressViewModel(progressRepository, userRepository)
    }

    @Test
    fun loadCourseData_updatesCourseData() = runTest {
        val user = UserEntity().apply { id = "user_123" }
        coEvery { userRepository.getUserModel() } returns user

        val expectedList = listOf(
            CoursesProgressRow(
                courseId = "course1",
                courseName = "Course Name 1",
                progressCurrent = null,
                progressMax = null,
                mistakes = null,
                stepMistake = null
            )
        )
        coEvery { progressRepository.getCourseProgressRows(user.id) } returns expectedList

        assertTrue(viewModel.courseData.value.isEmpty())

        viewModel.loadCourseData()

        advanceUntilIdle()

        io.mockk.coVerify { progressRepository.getCourseProgressRows("user_123") }

        assertEquals(expectedList, viewModel.courseData.value)
    }
}
