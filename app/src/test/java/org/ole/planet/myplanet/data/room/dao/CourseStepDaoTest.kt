package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.CourseStep
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CourseStepDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var courseStepDao: CourseStepDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        courseStepDao = database.courseStepDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createStep(id: String, courseId: String): CourseStep {
        return CourseStep().apply {
            this.id = id
            this.courseId = courseId
        }
    }

    @Test
    fun getByCourseIds_handles1200Courses() = runBlocking {
        val steps = (1..1200).map { i ->
            createStep("step_$i", "course_$i")
        }
        courseStepDao.upsertAll(steps)

        val courseIds = (1..1200).map { "course_$it" }
        val results = courseStepDao.getByCourseIds(courseIds)

        assertEquals(1200, results.size)
    }

    @Test
    fun getByCourseIds_emptyInput_returnsEmptyList() = runBlocking {
        val step = createStep("step_1", "course_1")
        courseStepDao.upsertAll(listOf(step))

        val results = courseStepDao.getByCourseIds(emptyList())

        assertTrue(results.isEmpty())
    }

    @Test
    fun getByCourseIds_duplicateIds_returnsNoDuplicateRows() = runBlocking {
        val step = createStep("step_1", "course_1")
        courseStepDao.upsertAll(listOf(step))

        val courseIds = listOf("course_1", "course_1", "course_1")
        val results = courseStepDao.getByCourseIds(courseIds)

        assertEquals(1, results.size)
        assertEquals("step_1", results[0].id)
    }
}
