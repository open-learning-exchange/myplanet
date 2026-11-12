package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.CourseProgress
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CourseProgressDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var courseProgressDao: CourseProgressDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        courseProgressDao = database.courseProgressDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createProgress(
        id: String,
        courseId: String,
        userId: String?,
        stepNum: Int = 1,
        passed: Boolean = true
    ): CourseProgress {
        return CourseProgress().apply {
            this.id = id
            this.courseId = courseId
            this.userId = userId
            this.stepNum = stepNum
            this.passed = passed
        }
    }

    @Test
    fun getByCourseUsersAndSteps_returnsExactTuplesAndExcludesCrossProduct() = runBlocking {
        val cp1 = createProgress("id1", "c1", "u1", 1)
        val cp2 = createProgress("id2", "c2", "u2", 2)
        val cpCross1 = createProgress("id3", "c1", "u2", 1)
        val cpCross2 = createProgress("id4", "c2", "u1", 2)

        courseProgressDao.upsertAll(listOf(cp1, cp2, cpCross1, cpCross2))

        val requestedTuples = listOf(
            Triple("c1", "u1", 1),
            Triple("c2", "u2", 2)
        )

        val results = courseProgressDao.getByCourseUsersAndSteps(requestedTuples)

        assertEquals(2, results.size)
        val resultIds = results.map { it.id }.toSet()
        assertTrue(resultIds.contains("id1"))
        assertTrue(resultIds.contains("id2"))
        assertFalse(resultIds.contains("id3"))
        assertFalse(resultIds.contains("id4"))
    }

    @Test
    fun getByCourseUsersAndSteps_emptyInput_returnsEmptyList() = runBlocking {
        val cp = createProgress("id1", "c1", "u1", 1)
        courseProgressDao.upsert(cp)

        val results = courseProgressDao.getByCourseUsersAndSteps(emptyList())

        assertTrue(results.isEmpty())
    }

    @Test
    fun getByCourseUsersAndSteps_handlesLargeTupleChunking() = runBlocking {
        val items = listOf(
            createProgress("id_1", "course_1", "user_1", 1),
            createProgress("id_250", "course_250", "user_250", 250),
            createProgress("id_251", "course_251", "user_251", 251),
            createProgress("id_300", "course_300", "user_300", 300)
        )
        courseProgressDao.upsertAll(items)

        val requestedTuples = (1..300).map { i ->
            Triple("course_$i", "user_$i", i)
        }

        val results = courseProgressDao.getByCourseUsersAndSteps(requestedTuples)

        assertEquals(4, results.size)
        assertEquals(
            setOf("id_1", "id_250", "id_251", "id_300"),
            results.map { it.id }.toSet()
        )
    }

    @Test
    fun getByUserAndCourseIds_handles1200CoursesWithNonNullUserId() = runBlocking {
        val items = listOf(
            createProgress("id_1", "course_1", "user_1"),
            createProgress("id_899", "course_899", "user_1"),
            createProgress("id_900", "course_900", "user_1"),
            createProgress("id_1200", "course_1200", "user_1"),
            createProgress("id_wrong_user", "course_1", "user_2")
        )
        courseProgressDao.upsertAll(items)

        val courseIds = (1..1200).map { "course_$it" }
        val results = courseProgressDao.getByUserAndCourseIds("user_1", courseIds)

        assertEquals(4, results.size)
        assertEquals(
            setOf("id_1", "id_899", "id_900", "id_1200"),
            results.map { it.id }.toSet()
        )
    }

    @Test
    fun getByUserAndCourseIds_handles1200CoursesWithNullUserId() = runBlocking {
        val items = listOf(
            createProgress("id_1", "course_1", null),
            createProgress("id_899", "course_899", null),
            createProgress("id_900", "course_900", null),
            createProgress("id_1200", "course_1200", null),
            createProgress("id_user_1_control", "course_1", "user_1")
        )
        courseProgressDao.upsertAll(items)

        val courseIds = (1..1200).map { "course_$it" }
        val results = courseProgressDao.getByUserAndCourseIds(null, courseIds)

        assertEquals(4, results.size)
        assertEquals(
            setOf("id_1", "id_899", "id_900", "id_1200"),
            results.map { it.id }.toSet()
        )
    }

    @Test
    fun getPendingUploads_includesUploadedRowsWithAChangedPassUntilTheyUpload() = runBlocking {
        courseProgressDao.upsertAll(listOf(
            CourseProgress().apply { id = "new"; userId = "user1" },
            CourseProgress().apply { id = "synced"; _id = "synced"; userId = "user1" },
            CourseProgress().apply { id = "changed"; _id = "changed"; isUpdated = true; userId = "user1" }
        ))

        assertEquals(setOf("new", "changed"), courseProgressDao.getPendingUploads().map { it.id }.toSet())

        courseProgressDao.markUploaded("changed", "changed", "2-abc")

        assertEquals(setOf("new"), courseProgressDao.getPendingUploads().map { it.id }.toSet())
    }

    @Test
    fun updatePassedByCourseAndStep_flagsAnUploadedRowOnlyWhenItsPassChanges() = runBlocking {
        courseProgressDao.upsertAll(listOf(
            createProgress("uploaded", "course1", "user1", stepNum = 1, passed = false).apply { _id = "uploaded" },
            createProgress("local", "course1", "user1", stepNum = 2, passed = false),
            createProgress("passed", "course1", "user1", stepNum = 3, passed = true).apply { _id = "passed" }
        ))

        (1..3).forEach { courseProgressDao.updatePassedByCourseAndStep("course1", it, true, "user1") }

        val rows = courseProgressDao.getByUserAndCourse("user1", "course1").associateBy { it.id }
        assertTrue(rows.getValue("uploaded").passed && rows.getValue("uploaded").isUpdated)
        assertTrue(rows.getValue("local").passed)
        assertFalse(rows.getValue("local").isUpdated)
        assertFalse(rows.getValue("passed").isUpdated)
    }
}
