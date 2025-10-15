package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse

@OptIn(ExperimentalCoroutinesApi::class)
class CourseRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: CourseRepositoryImpl

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        repository = CourseRepositoryImpl(databaseService)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun getCourseByCourseId_returnsCourseWhenIdProvided() = runTest {
        val expected = RealmMyCourse().apply { courseId = "course-1" }
        coEvery { databaseService.withRealmAsync<RealmMyCourse?>(any()) } returns expected

        val result = repository.getCourseByCourseId("course-1")

        assertEquals(expected, result)
        coVerify(exactly = 1) { databaseService.withRealmAsync<RealmMyCourse?>(any()) }
    }

    @Test
    fun getCourseExamCount_returnsExamTotal() = runTest {
        coEvery { databaseService.withRealmAsync<Long>(any()) } returns 4L

        val result = repository.getCourseExamCount("course-1")

        assertEquals(4, result)
        coVerify(exactly = 1) { databaseService.withRealmAsync<Long>(any()) }
    }

    @Test
    fun getCourseSteps_returnsEmptyListWhenCourseIdMissing() = runTest {
        val result = repository.getCourseSteps(null)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { databaseService.withRealmAsync<List<RealmCourseStep>>(any()) }
    }
}
