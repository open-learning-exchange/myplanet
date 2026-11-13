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

    @Test
    fun countByCourseIds_handlesMoreThan900Ids() = runBlocking {
        val steps = (1..1200).flatMap { i ->
            listOf(createStep("step_${i}_a", "course_$i"), createStep("step_${i}_b", "course_$i"))
        }
        courseStepDao.upsertAll(steps)

        val courseIds = (1..1200).map { "course_$it" }
        val counts = courseStepDao.countByCourseIds(courseIds)

        assertEquals(1200, counts.size)
        assertEquals(2, counts["course_1"])
        assertEquals(2, counts["course_1200"])
    }

    @Test
    fun countByCourseIds_emptyInput_returnsEmptyMap() = runBlocking {
        courseStepDao.upsertAll(listOf(createStep("step_1", "course_1")))

        assertTrue(courseStepDao.countByCourseIds(emptyList()).isEmpty())
    }

    @Test
    fun deleteStaleSteps_removesOnlyThatCoursesOldSteps() = runBlocking {
        courseStepDao.upsertAll(listOf(
            createStep("old", "c1"), createStep("kept", "c1"), createStep("other", "c2")
        ))

        courseStepDao.deleteStaleSteps("c1", listOf("kept"))

        assertEquals(listOf("kept"), courseStepDao.getByCourseId("c1").map { it.id })
        assertEquals(listOf("other"), courseStepDao.getByCourseId("c2").map { it.id })
    }

    @Test
    fun deleteStaleSteps_withNoStepsLeft_clearsTheCourse() = runBlocking {
        courseStepDao.upsertAll(listOf(createStep("a", "c1"), createStep("b", "c1")))

        courseStepDao.deleteStaleSteps("c1", emptyList())

        assertEquals(emptyList<String>(), courseStepDao.getByCourseId("c1").map { it.id })
    }

    @Test
    fun deleteStaleSteps_handlesOver33000KeepIds_retainsBoundaryStepsAndLeavesOtherCourseUntouched() = runBlocking {
        courseStepDao.upsertAll(listOf(
            createStep("step_first", "c1"),
            createStep("step_stale", "c1"),
            createStep("step_last", "c1"),
            createStep("step_c2", "c2")
        ))

        val dummyKeepIds = (1..33000).map { "dummy_$it" }
        val keepIds = listOf("step_first") + dummyKeepIds + listOf("step_last")

        courseStepDao.deleteStaleSteps("c1", keepIds)

        val c1Steps = courseStepDao.getByCourseId("c1").map { it.id }.toSet()
        assertEquals(setOf("step_first", "step_last"), c1Steps)
        assertEquals(listOf("step_c2"), courseStepDao.getByCourseId("c2").map { it.id })
    }

    @Test
    fun deleteStaleSteps_removesOver900StaleSteps_leavesOtherCourseUntouched() = runBlocking {
        val staleSteps = (1..950).map { createStep("stale_$it", "c1") }
        courseStepDao.upsertAll(staleSteps + listOf(
            createStep("kept_step", "c1"),
            createStep("other_course_step", "c2")
        ))

        courseStepDao.deleteStaleSteps("c1", listOf("kept_step"))

        assertEquals(listOf("kept_step"), courseStepDao.getByCourseId("c1").map { it.id })
        assertEquals(listOf("other_course_step"), courseStepDao.getByCourseId("c2").map { it.id })
    }

    @Test
    fun deleteStaleSteps_emptyKeepList_leavesOtherCourseUntouched() = runBlocking {
        courseStepDao.upsertAll(listOf(
            createStep("a", "c1"),
            createStep("b", "c1"),
            createStep("c2_step", "c2")
        ))

        courseStepDao.deleteStaleSteps("c1", emptyList())

        assertTrue(courseStepDao.getByCourseId("c1").isEmpty())
        assertEquals(listOf("c2_step"), courseStepDao.getByCourseId("c2").map { it.id })
    }
}
