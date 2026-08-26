package org.ole.planet.myplanet.ui.courses

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
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
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var viewModel: ProgressViewModel
    private val progressRepository: ProgressRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        viewModel = ProgressViewModel(progressRepository, userRepository, Gson(), dispatcherProvider)
    }

    @Test
    fun loadCourseData_updatesCourseData() = runTest {
        val user = UserEntity().apply { id = "user_123" }
        coEvery { userRepository.getUserModel() } returns user

        val jsonObject = JsonObject().apply {
            addProperty("courseId", "course1")
            addProperty("courseName", "Course Name 1")
        }
        val expectedJsonArray = JsonArray().apply { add(jsonObject) }
        coEvery { progressRepository.fetchCourseData(user.id) } returns expectedJsonArray

        assertTrue(viewModel.courseData.value.isEmpty())

        viewModel.loadCourseData()

        advanceUntilIdle()

        io.mockk.coVerify { progressRepository.fetchCourseData("user_123") }

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
        assertEquals(expectedList, viewModel.courseData.value)
    }

    @Test
    fun loadCourseData_parsesJsonCorrectly() = runTest {
        val user = UserEntity().apply { id = "user_123" }
        coEvery { userRepository.getUserModel() } returns user

        val jsonObject = JsonObject().apply {
            addProperty("courseId", "c1")
            addProperty("courseName", "Course 1")
            add("progress", JsonObject().apply {
                addProperty("current", 5)
                addProperty("max", 10)
            })
            addProperty("mistakes", 2)
            add("stepMistake", JsonObject().apply {
                addProperty("step1", 1)
                addProperty("step2", 0)
            })
        }
        val expectedJsonArray = JsonArray().apply { add(jsonObject) }
        coEvery { progressRepository.fetchCourseData(user.id) } returns expectedJsonArray

        viewModel.loadCourseData()
        advanceUntilIdle()

        val expectedList = listOf(
            CoursesProgressRow(
                courseId = "c1",
                courseName = "Course 1",
                progressCurrent = 5,
                progressMax = 10,
                mistakes = 2,
                stepMistake = mapOf("step1" to 1, "step2" to 0)
            )
        )
        assertEquals(expectedList, viewModel.courseData.value)
    }
}
