package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyCourse
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32])
class CourseDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var courseDao: CourseDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        courseDao = database.courseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun userIdPattern(userId: String): String {
        val escaped = userId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%\"$escaped\"%"
    }

    @Test
    fun getForUserPattern_filtersCorrectly() = runBlocking {
        val course1 = MyCourse(
            id = UUID.randomUUID().toString(),
            courseTitle = "Course 1",
            userId = listOf("user1", "user2")
        )
        val course2 = MyCourse(
            id = UUID.randomUUID().toString(),
            courseTitle = "Course 2",
            userId = listOf("user2")
        )
        val course3 = MyCourse(
            id = UUID.randomUUID().toString(),
            courseTitle = "Course 3",
            userId = listOf("user3")
        )
        val course4 = MyCourse(
            id = UUID.randomUUID().toString(),
            courseTitle = "Course 4",
            userId = listOf("user1_test")
        )
        val course5 = MyCourse(
            id = UUID.randomUUID().toString(),
            courseTitle = "Course 5",
            userId = listOf("user1%test")
        )
        val course6 = MyCourse(
            id = UUID.randomUUID().toString(),
            courseTitle = "Course 6",
            userId = listOf("user1Xtest")
        )

        courseDao.upsertAll(listOf(course1, course2, course3, course4, course5, course6))

        val resultUser1 = courseDao.getForUserPattern(userIdPattern("user1"))
        assertEquals(1, resultUser1.size)
        assertTrue(resultUser1.any { it.id == course1.id })

        val resultUser2 = courseDao.getForUserPattern(userIdPattern("user2"))
        assertEquals(2, resultUser2.size)
        assertTrue(resultUser2.any { it.id == course1.id })
        assertTrue(resultUser2.any { it.id == course2.id })

        val resultUser1Underscore = courseDao.getForUserPattern(userIdPattern("user1_test"))
        assertEquals(1, resultUser1Underscore.size)
        assertTrue(resultUser1Underscore.any { it.id == course4.id })

        val resultUser1Percent = courseDao.getForUserPattern(userIdPattern("user1%test"))
        assertEquals(1, resultUser1Percent.size)
        assertTrue(resultUser1Percent.any { it.id == course5.id })
    }
}
