package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.SubmitPhotos
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubmitPhotosDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var submitPhotosDao: SubmitPhotosDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        submitPhotosDao = database.submitPhotosDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getByIds_handlesEmptyInput() = runBlocking {
        val result = submitPhotosDao.getByIds(emptyArray())
        assertTrue(result.isEmpty())
    }

    @Test
    fun getByIds_handlesSingleChunkList() = runBlocking {
        val items = (1..50).map { i ->
            SubmitPhotos().apply { id = "photo_$i" }
        }
        items.forEach { submitPhotosDao.insert(it) }

        val ids = items.map { it.id }.toTypedArray()
        val result = submitPhotosDao.getByIds(ids)
        assertEquals(50, result.size)
        assertEquals(ids.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun getByIds_handlesLargeChunkedList() = runBlocking {
        val items = (1..1000).map { i ->
            SubmitPhotos().apply { id = "photo_$i" }
        }
        items.forEach { submitPhotosDao.insert(it) }

        val ids = items.map { it.id }.toTypedArray()
        val result = submitPhotosDao.getByIds(ids)
        assertEquals(1000, result.size)
        assertEquals(ids.toSet(), result.map { it.id }.toSet())
    }
}
