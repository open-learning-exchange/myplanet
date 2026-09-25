package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyLibrary
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32])
class MyLibraryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var myLibraryDao: MyLibraryDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        myLibraryDao = database.myLibraryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getLibraryTitles_returnsOneEntryPerRowWithIdAndTitle() = runBlocking {
        val lib1 = MyLibrary().apply {
            id = "pk1"
            _id = "pk1"
            resourceId = "res1"
            title = "First Resource"
            description = "Long description 1"
        }
        val lib2 = MyLibrary().apply {
            id = "pk2"
            _id = "pk2"
            resourceId = "res2"
            title = "Second Resource"
            description = "Long description 2"
        }

        myLibraryDao.upsertAll(listOf(lib1, lib2))

        val projections = myLibraryDao.getLibraryTitles()

        assertEquals(2, projections.size)
        val projMap = projections.associate { it.id to it.title }
        assertEquals("First Resource", projMap["pk1"])
        assertEquals("Second Resource", projMap["pk2"])
    }

    @Test
    fun getLibraryItemsByIds_roundTripsProjectionIdsBackToFullEntitiesWhenIdDiffersFromUnderscoreId() = runBlocking {
        val lib1 = MyLibrary().apply {
            id = "pk1"
            _id = "doc1"
            resourceId = "res1"
            title = "First Resource"
            description = "Description 1"
            author = "Author 1"
        }
        val lib2 = MyLibrary().apply {
            id = "pk2"
            _id = "doc2"
            resourceId = "res2"
            title = "Second Resource"
            description = "Description 2"
            author = "Author 2"
        }

        myLibraryDao.upsertAll(listOf(lib1, lib2))

        val projections = myLibraryDao.getLibraryTitles()
        val projectionIds = projections.map { it.id }

        val fullEntities = myLibraryDao.getByIds(projectionIds)

        assertEquals(2, fullEntities.size)
        val entityMap = fullEntities.associateBy { it.id }

        val fetchedLib1 = entityMap["pk1"]
        assertNotNull(fetchedLib1)
        assertEquals("First Resource", fetchedLib1?.title)
        assertEquals("Description 1", fetchedLib1?.description)
        assertEquals("Author 1", fetchedLib1?.author)

        val fetchedLib2 = entityMap["pk2"]
        assertNotNull(fetchedLib2)
        assertEquals("Second Resource", fetchedLib2?.title)
        assertEquals("Description 2", fetchedLib2?.description)
        assertEquals("Author 2", fetchedLib2?.author)
    }

    @Test
    fun getByCourseIds_handlesLargeInputAndDeduplicates() = runBlocking {
        val lib1 = MyLibrary().apply { id = "pk_0"; courseId = "course_0" }
        val lib2 = MyLibrary().apply { id = "pk_1000"; courseId = "course_1000" }
        myLibraryDao.upsertAll(listOf(lib1, lib2))

        val queryIds = (0 until 1200).map { "course_$it" } + listOf("course_0", "course_1000")
        val result = myLibraryDao.getByCourseIds(queryIds)

        assertEquals(2, result.size)
        assertTrue(result.any { it.courseId == "course_0" })
        assertTrue(result.any { it.courseId == "course_1000" })
    }

    @Test
    fun getOfflineResourcesForCourses_handlesLargeInputAndDeduplicates() = runBlocking {
        val lib1 = MyLibrary().apply { id = "pk_0"; courseId = "course_0"; resourceOffline = false; resourceLocalAddress = "local/path_0" }
        val lib2 = MyLibrary().apply { id = "pk_1000"; courseId = "course_1000"; resourceOffline = false; resourceLocalAddress = "local/path_1000" }
        myLibraryDao.upsertAll(listOf(lib1, lib2))

        val queryIds = (0 until 1200).map { "course_$it" } + listOf("course_0", "course_1000")
        val result = myLibraryDao.getOfflineResourcesForCourses(queryIds)

        assertEquals(2, result.size)
        assertTrue(result.any { it.courseId == "course_0" })
        assertTrue(result.any { it.courseId == "course_1000" })
    }
}
