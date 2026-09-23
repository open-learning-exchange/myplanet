package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.Feedback
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedbackDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var feedbackDao: FeedbackDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        feedbackDao = database.feedbackDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun closeById_marksFeedbackPendingSoTheCloseUploads() = runBlocking {
        feedbackDao.upsert(Feedback().apply { id = "fb1"; status = "Open"; isUploaded = true })

        feedbackDao.closeById("fb1")

        val closed = feedbackDao.findById("fb1")!!
        assertEquals("Closed", closed.status)
        assertFalse(closed.isUploaded)
        assertEquals(listOf("fb1"), feedbackDao.getPending().map { it.id })
    }

    @Test
    fun markUploaded_storesServerIdAndRev() = runBlocking {
        feedbackDao.upsert(Feedback().apply { id = "local-uuid"; isUploaded = false })

        val updated = feedbackDao.markUploaded("local-uuid", "server-id", "1-abc")

        val row = feedbackDao.findById("local-uuid")!!
        assertEquals(1, updated)
        assertTrue(row.isUploaded)
        assertEquals("server-id", row._id)
        assertEquals("1-abc", row._rev)
    }

    @Test
    fun markUploaded_blankServerValues_keepsExistingIdAndRev() = runBlocking {
        feedbackDao.upsert(Feedback().apply { id = "fb1"; _id = "fb1"; _rev = "2-def"; isUploaded = false })

        feedbackDao.markUploaded("fb1", "", "")

        val row = feedbackDao.findById("fb1")!!
        assertTrue(row.isUploaded)
        assertEquals("fb1", row._id)
        assertEquals("2-def", row._rev)
    }
}
