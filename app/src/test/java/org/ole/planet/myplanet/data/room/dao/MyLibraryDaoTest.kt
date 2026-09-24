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
}
