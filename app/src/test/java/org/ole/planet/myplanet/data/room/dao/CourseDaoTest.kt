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

    @Test
    fun getByCourseIds_withLargeList_returnsAllWithoutThrowing() = runBlocking {
        val courses = (1..1200).map { i ->
            MyCourse(
                id = "id_$i",
                courseId = "courseId_$i",
                _id = "_id_$i"
            )
        }
        courseDao.upsertAll(courses)

        val searchIds = (1..1200).map { "id_$it" }
        val result = courseDao.getByCourseIds(searchIds)
        assertEquals(1200, result.size)
    }

    @Test
    fun getByCourseIds_doesNotDuplicateWhenMatchedByDifferentColumnsInDifferentChunks() = runBlocking {
        val course = MyCourse(
            id = "course_pk",
            courseId = "course_cid",
            _id = "course_doc_id"
        )
        courseDao.upsertAll(listOf(course))

        // Build list of >300 IDs such that "course_cid" is in chunk 1 and "course_doc_id" is in chunk 2
        val idsChunk1 = listOf("course_cid") + (1..299).map { "dummy_chunk1_$it" }
        val idsChunk2 = listOf("course_doc_id") + (1..100).map { "dummy_chunk2_$it" }
        val allIds = idsChunk1 + idsChunk2

        val result = courseDao.getByCourseIds(allIds)
        assertEquals(1, result.size)
        assertEquals("course_pk", result[0].id)
    }

    @Test
    fun getByCourseIds_withEmptyList_returnsEmptyList() = runBlocking {
        val result = courseDao.getByCourseIds(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun getByCourseIds_matchesAcrossAllThreeColumns() = runBlocking {
        val courseByCourseId = MyCourse(id = "pk1", courseId = "cid1", _id = "doc1")
        val courseById = MyCourse(id = "pk2", courseId = "cid2", _id = "doc2")
        val courseByDocId = MyCourse(id = "pk3", courseId = "cid3", _id = "doc3")

        courseDao.upsertAll(listOf(courseByCourseId, courseById, courseByDocId))

        val result = courseDao.getByCourseIds(listOf("cid1", "pk2", "doc3"))
        assertEquals(3, result.size)
        val resultIds = result.map { it.id }.toSet()
        assertEquals(setOf("pk1", "pk2", "pk3"), resultIds)
    }
}
