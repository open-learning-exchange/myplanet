package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyLibrary

@RunWith(AndroidJUnit4::class)
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
    fun getByIds_handles1200Ids() = runBlocking {
        val libraries = (1..1200).map { i ->
            MyLibrary().apply {
                id = "pk_$i"
                title = "Library $i"
            }
        }
        myLibraryDao.upsertAll(libraries)

        val queryIds = (1..1200).map { "pk_$it" } + listOf("pk_1", "pk_1200")
        val results = myLibraryDao.getByIds(queryIds)

        assertEquals(1200, results.size)
        assertEquals(1200, results.map { it.id }.distinct().size)

        val emptyResult = myLibraryDao.getByIds(emptyList())
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun getByResourceIds_handles1200Ids() = runBlocking {
        val libraries = (1..1200).map { i ->
            MyLibrary().apply {
                id = "pk_$i"
                resourceId = "res_$i"
                title = "Library $i"
            }
        }
        myLibraryDao.upsertAll(libraries)

        val resourceIds = (1..1200).map { "res_$it" } + listOf("res_1", "res_1200")
        val results = myLibraryDao.getByResourceIds(resourceIds)

        assertEquals(1200, results.size)
        assertEquals(1200, results.map { it.resourceId }.distinct().size)

        val emptyResult = myLibraryDao.getByResourceIds(emptyList())
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun getByResourceIdsNotUserPattern_handles1200Ids_andExcludesUser() = runBlocking {
        val userPattern = "%\"user_123\"%"
        val libraries = (1..1200).map { i ->
            MyLibrary().apply {
                id = "pk_$i"
                resourceId = "res_$i"
                // Even indexed items belong to user_123, odd indexed items do not
                userId = if (i % 2 == 0) listOf("user_123") else listOf("user_456")
            }
        }
        myLibraryDao.upsertAll(libraries)

        val resourceIds = (1..1200).map { "res_$it" } + listOf("res_1", "res_2")
        val results = myLibraryDao.getByResourceIdsNotUserPattern(resourceIds, userPattern)

        assertEquals(600, results.size)
        assertTrue(results.all { it.userId == listOf("user_456") })

        val emptyResult = myLibraryDao.getByResourceIdsNotUserPattern(emptyList(), userPattern)
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun getByCourseIds_handles1200Courses() = runBlocking {
        val libraries = (1..1200).map { i ->
            MyLibrary().apply {
                id = "lib_$i"
                courseId = "course_$i"
            }
        }
        myLibraryDao.upsertAll(libraries)

        val courseIds = (1..1200).map { "course_$it" }
        val results = myLibraryDao.getByCourseIds(courseIds)

        assertEquals(1200, results.size)
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
    fun getByResourceIdsByRowid_returnsRowsInInsertionOrder() = runBlocking {
        val lib1 = MyLibrary().apply { id = "pk1"; resourceId = "resA"; title = "First" }
        val lib2 = MyLibrary().apply { id = "pk2"; resourceId = "resB"; title = "Second" }
        val lib3 = MyLibrary().apply { id = "pk3"; resourceId = "resA"; title = "Third" }

        myLibraryDao.upsert(lib1)
        myLibraryDao.upsert(lib2)
        myLibraryDao.upsert(lib3)

        val results = myLibraryDao.getByResourceIdsByRowid(listOf("resA", "resB"))

        assertEquals(3, results.size)
        assertEquals("pk1", results[0].id)
        assertEquals("pk2", results[1].id)
        assertEquals("pk3", results[2].id)
    }

    @Test
    fun getByResourceIdsByRowid_handles1200Ids() = runBlocking {
        val items = (1..1200).map { i ->
            MyLibrary().apply {
                id = "pk_$i"
                resourceId = "res_$i"
                title = "Resource $i"
            }
        }
        myLibraryDao.upsertAll(items)

        val searchIds = items.map { it.resourceId!! }
        val results = myLibraryDao.getByResourceIdsByRowid(searchIds)

        assertEquals(1200, results.size)
        assertEquals("pk_1", results.first().id)
        assertEquals("pk_1200", results.last().id)
    }

    @Test
    fun markAsNotOfflineByResourceIds_and_getResourceTitlesByResourceIds_deduplicateAcrossChunkBoundary() = runBlocking {
        val count = 1000
        val items = (1..count).map { i ->
            MyLibrary().apply {
                id = "off_id_$i"
                _id = "off_doc_$i"
                resourceId = "off_res_$i"
                title = "Title $i"
                resourceOffline = true
            }
        }
        myLibraryDao.upsertAll(items)

        val idsWithDuplicate = items.map { it.resourceId!! } + "off_res_1"
        assertEquals(1001, idsWithDuplicate.size)

        val titles = myLibraryDao.getResourceTitlesByResourceIds(idsWithDuplicate)
        assertEquals(1000, titles.size)

        myLibraryDao.markAsNotOfflineByResourceIds(idsWithDuplicate)

        val allItems = myLibraryDao.getPublic()
        assertEquals(count, allItems.size)
        allItems.forEach { item ->
            assertEquals(false, item.resourceOffline)
        }
    }

    @Test
    fun getResourceTitlesByResourceIds_returnsTitlesOrderedByRowidAndHandlesMoreThan900Ids() = runBlocking {
        val dup1 = MyLibrary().apply {
            id = "pk1"
            _id = "pk1"
            resourceId = "shared_res"
            title = "Old Title"
        }
        val dup2 = MyLibrary().apply {
            id = "pk2"
            _id = "pk2"
            resourceId = "shared_res"
            title = "New Title"
        }

        val items = (1..950).map { i ->
            MyLibrary().apply {
                id = "item_$i"
                _id = "item_$i"
                resourceId = "res_$i"
                title = "Title $i"
            }
        }.toMutableList()

        items.add(0, dup1)
        items.add(dup2)

        myLibraryDao.upsertAll(items)

        val targetIds = (1..950).map { "res_$it" } + listOf("shared_res")
        val results = myLibraryDao.getResourceTitlesByResourceIds(targetIds)

        assertEquals(952, results.size)

        val sharedResTitles = results.filter { it.resourceId == "shared_res" }.map { it.title }
        assertEquals(listOf("Old Title", "New Title"), sharedResTitles)

        val titleMap = results.associate { (it.resourceId ?: "") to (it.title ?: "") }
        assertEquals("New Title", titleMap["shared_res"])
        assertEquals("Title 950", titleMap["res_950"])
    }

    @Test
    fun deleteStalePublicNotIn_deletesOnlyStalePublicSyncedResources() = runBlocking {
        val items = mutableListOf<MyLibrary>()

        // 1,500 "current" public synced rows
        val currentIds = (1..1500).map { i -> "current_res_$i" }
        currentIds.forEachIndexed { idx, resId ->
            items.add(MyLibrary().apply {
                id = "curr_id_$idx"
                _id = "curr_doc_$idx"
                resourceId = resId
                _rev = "1-rev"
                isPrivate = false
            })
        }

        // 3 stale public synced rows
        items.add(MyLibrary().apply {
            id = "stale_1"
            _id = "stale_doc_1"
            resourceId = "stale_res_1"
            _rev = "1-rev"
            isPrivate = false
        })
        items.add(MyLibrary().apply {
            id = "stale_2"
            _id = "stale_doc_2"
            resourceId = "stale_res_2"
            _rev = "1-rev"
            isPrivate = false
        })
        items.add(MyLibrary().apply {
            id = "stale_3"
            _id = "stale_doc_3"
            resourceId = "stale_res_3"
            _rev = "1-rev"
            isPrivate = false
        })

        // 1 stale private row
        items.add(MyLibrary().apply {
            id = "private_1"
            _id = "private_doc_1"
            resourceId = "private_res_1"
            _rev = "1-rev"
            isPrivate = true
        })

        // 1 stale unsynced row (_rev = "")
        items.add(MyLibrary().apply {
            id = "unsynced_1"
            _id = "unsynced_doc_1"
            resourceId = "unsynced_res_1"
            _rev = ""
            isPrivate = false
        })

        // 1 row with a NULL resourceId
        items.add(MyLibrary().apply {
            id = "null_res_1"
            _id = "null_res_doc_1"
            resourceId = null
            _rev = "1-rev"
            isPrivate = false
        })

        myLibraryDao.upsertAll(items)

        myLibraryDao.deleteStalePublicNotIn(currentIds)

        val remainingPublic = myLibraryDao.getPublic()
        val remainingPublicIds = remainingPublic.map { it.id }.toSet()

        // 1500 current + 1 unsynced + 1 null resourceId remain public; the 3 stale ones are gone
        assertEquals(1502, remainingPublic.size)

        // Assert that stale public rows are deleted
        assertFalse(remainingPublicIds.contains("stale_1"))
        assertFalse(remainingPublicIds.contains("stale_2"))
        assertFalse(remainingPublicIds.contains("stale_3"))

        // Assert non-stale / non-candidate rows survive
        assertTrue(remainingPublicIds.contains("unsynced_1"))
        assertTrue(remainingPublicIds.contains("null_res_1"))
        assertNotNull(myLibraryDao.getById("private_1"))
    }

    @Test
    fun markAsNotOfflineByResourceIds_clearsResourceOfflineStatusForGivenIds() = runBlocking {
        val count = 1200
        val items = (1..count).map { i ->
            MyLibrary().apply {
                id = "off_id_$i"
                _id = "off_doc_$i"
                resourceId = "off_res_$i"
                resourceOffline = true
            }
        }
        myLibraryDao.upsertAll(items)

        val idsToMark = items.map { it.resourceId!! }
        myLibraryDao.markAsNotOfflineByResourceIds(idsToMark)

        val allItems = myLibraryDao.getPublic()
        assertEquals(count, allItems.size)
        allItems.forEach { item ->
            assertEquals(false, item.resourceOffline)
        }
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
