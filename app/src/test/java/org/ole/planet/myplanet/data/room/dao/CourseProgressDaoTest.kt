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
        userId: String,
        stepNum: Int,
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
        val items = (1..300).map { i ->
            createProgress("id_$i", "course_$i", "user_$i", i)
        }
        courseProgressDao.upsertAll(items)

        val requestedTuples = (1..300).map { i ->
            Triple("course_$i", "user_$i", i)
        }

        val results = courseProgressDao.getByCourseUsersAndSteps(requestedTuples)

        assertEquals(300, results.size)
    }
}
