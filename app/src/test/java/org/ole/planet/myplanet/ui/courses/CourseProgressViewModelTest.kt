package org.ole.planet.myplanet.ui.courses

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.MainDispatcherRule

class CourseProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coursesRepository = mockk<CoursesRepository>()
    private val userSessionManager = mockk<UserSessionManager>()
    private val viewModel = CourseProgressViewModel(coursesRepository, userSessionManager)

    @Test
    fun `courseProgress value is null before any call`() {
        assertNull(viewModel.courseProgress.value)
    }

    @Test
    fun `loadProgress sets courseProgress value correctly`() = runTest {
        val courseId = "id"
        val userId = "userId"
        val user = RealmUser()
        user._id = userId
        coEvery { userSessionManager.getUserModel() } returns user

        val expectedProgressData = mockk<CourseProgressData>()
        coEvery { coursesRepository.getCourseProgress(courseId, userId) } returns expectedProgressData

        viewModel.loadProgress(courseId)

        assertEquals(expectedProgressData, viewModel.courseProgress.value)
    }

    @Test
    fun `calling loadProgress twice only invokes coursesRepository once`() = runTest {
        val courseId = "id"
        val userId = "userId"
        val user = RealmUser()
        user._id = userId
        coEvery { userSessionManager.getUserModel() } returns user

        val expectedProgressData = mockk<CourseProgressData>()
        coEvery { coursesRepository.getCourseProgress(courseId, userId) } returns expectedProgressData

        // Call loadProgress for the first time
        viewModel.loadProgress(courseId)
        assertEquals(expectedProgressData, viewModel.courseProgress.value)

        // Call loadProgress for the second time
        viewModel.loadProgress(courseId)

        // Verify repository method is called only once
        coVerify(exactly = 1) { coursesRepository.getCourseProgress(courseId, userId) }
    }
}
