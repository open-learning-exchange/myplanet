package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.Rating
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RatingDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var ratingDao: RatingDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        ratingDao = database.ratingDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun markUploaded_rekeysRowOntoServerIdAndRev() = runBlocking {
        ratingDao.upsert(Rating().apply { id = "local-uuid"; isUpdated = true; rate = 4 })

        val updated = ratingDao.markUploaded("local-uuid", "server-id", "1-abc")

        assertEquals(1, updated)
        assertNull(ratingDao.findById("local-uuid"))
        val row = ratingDao.findById("server-id")!!
        assertEquals("server-id", row._id)
        assertEquals("1-abc", row._rev)
        assertFalse(row.isUpdated)
        assertEquals(4, row.rate)
    }

    @Test
    fun markUploaded_thenPull_keepsASingleRow() = runBlocking {
        ratingDao.upsert(Rating().apply { id = "local-uuid"; isUpdated = true; type = "resource"; item = "r1"; userId = "u1" })
        ratingDao.markUploaded("local-uuid", "server-id", "1-abc")

        ratingDao.upsertAll(listOf(Rating().apply { id = "server-id"; _id = "server-id"; _rev = "1-abc"; type = "resource"; item = "r1"; userId = "u1" }))

        assertEquals(1, ratingDao.getByType("resource").size)
    }

    @Test
    fun markUploaded_blankServerId_onlyClearsPendingFlag() = runBlocking {
        ratingDao.upsert(Rating().apply { id = "local-uuid"; _rev = "1-old"; isUpdated = true })

        ratingDao.markUploaded("local-uuid", "", "")

        val row = ratingDao.findById("local-uuid")!!
        assertNull(row._id)
        assertEquals("1-old", row._rev)
        assertFalse(row.isUpdated)
    }

    @Test
    fun deleteUploadedWithoutServerId_removesOnlyUploadedOrphans() = runBlocking {
        ratingDao.upsert(Rating().apply { id = "orphan"; isUpdated = false })
        ratingDao.upsert(Rating().apply { id = "pending"; isUpdated = true })
        ratingDao.upsert(Rating().apply { id = "synced"; _id = "synced"; isUpdated = false })

        ratingDao.deleteUploadedWithoutServerId()

        assertNull(ratingDao.findById("orphan"))
        assertEquals("pending", ratingDao.findById("pending")?.id)
        assertEquals("synced", ratingDao.findById("synced")?.id)
    }
}
