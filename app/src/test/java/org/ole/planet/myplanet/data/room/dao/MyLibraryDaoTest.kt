package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                isPrivate = 0
            })
        }

        // 3 stale public synced rows
        items.add(MyLibrary().apply {
            id = "stale_1"
            _id = "stale_doc_1"
            resourceId = "stale_res_1"
            _rev = "1-rev"
            isPrivate = 0
        })
        items.add(MyLibrary().apply {
            id = "stale_2"
            _id = "stale_doc_2"
            resourceId = "stale_res_2"
            _rev = "1-rev"
            isPrivate = 0
        })
        items.add(MyLibrary().apply {
            id = "stale_3"
            _id = "stale_doc_3"
            resourceId = "stale_res_3"
            _rev = "1-rev"
            isPrivate = 0
        })

        // 1 stale private row
        items.add(MyLibrary().apply {
            id = "private_1"
            _id = "private_doc_1"
            resourceId = "private_res_1"
            _rev = "1-rev"
            isPrivate = 1
        })

        // 1 stale unsynced row (_rev = "")
        items.add(MyLibrary().apply {
            id = "unsynced_1"
            _id = "unsynced_doc_1"
            resourceId = "unsynced_res_1"
            _rev = ""
            isPrivate = 0
        })

        // 1 row with a NULL resourceId
        items.add(MyLibrary().apply {
            id = "null_res_1"
            _id = "null_res_doc_1"
            resourceId = null
            _rev = "1-rev"
            isPrivate = 0
        })

        myLibraryDao.upsertAll(items)

        myLibraryDao.deleteStalePublicNotIn(currentIds)

        val remaining = myLibraryDao.getAll()
        val remainingIds = remaining.map { it.id }.toSet()

        // Assert exactly 1503 items remain (1500 current + 1 private + 1 unsynced + 1 null resourceId)
        assertEquals(1503, remaining.size)

        // Assert that stale public rows are deleted
        org.junit.Assert.assertFalse(remainingIds.contains("stale_1"))
        org.junit.Assert.assertFalse(remainingIds.contains("stale_2"))
        org.junit.Assert.assertFalse(remainingIds.contains("stale_3"))

        // Assert non-stale / non-candidate rows survive
        org.junit.Assert.assertTrue(remainingIds.contains("private_1"))
        org.junit.Assert.assertTrue(remainingIds.contains("unsynced_1"))
        org.junit.Assert.assertTrue(remainingIds.contains("null_res_1"))
    }

    @Test
    fun markAsNotOfflineByResourceIds_clearsResourceOfflineStatusForGivenIds() = runBlocking {
        val count = 1200
        val items = (1..count).map { i ->
            MyLibrary().apply {
                id = "off_id_$i"
                _id = "off_doc_$i"
                resourceId = "off_res_$i"
                resourceOffline = 1
            }
        }
        myLibraryDao.upsertAll(items)

        val idsToMark = items.map { it.resourceId!! }
        myLibraryDao.markAsNotOfflineByResourceIds(idsToMark)

        val allItems = myLibraryDao.getAll()
        assertEquals(count, allItems.size)
        allItems.forEach { item ->
            assertEquals(0, item.resourceOffline)
        }
    }
}
