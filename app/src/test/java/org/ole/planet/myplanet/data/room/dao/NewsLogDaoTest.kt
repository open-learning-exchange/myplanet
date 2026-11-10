package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.NewsLog

@RunWith(AndroidJUnit4::class)
class NewsLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var newsLogDao: NewsLogDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        newsLogDao = database.newsLogDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getPendingUploads_returnsPendingLogsOrderedOldestFirstTiebreakByIdAndNullTimeLast() = runBlocking {
        // Insert out of chronological order
        val log1 = NewsLog().apply {
            id = "log_c"
            time = 1000L
            _id = null
        }
        val log2 = NewsLog().apply {
            id = "log_a"
            time = 500L
            _id = ""
        }
        val log3TieBreakB = NewsLog().apply {
            id = "log_b"
            time = 800L
            _id = null
        }
        val log3TieBreakA = NewsLog().apply {
            id = "log_a_tie"
            time = 800L
            _id = null
        }
        val logNullTime = NewsLog().apply {
            id = "log_null_time"
            time = null
            _id = null
        }
        val logUploaded = NewsLog().apply {
            id = "log_uploaded"
            time = 100L
            _id = "remote_id_123"
        }

        newsLogDao.insert(log1)
        newsLogDao.insert(log2)
        newsLogDao.insert(log3TieBreakB)
        newsLogDao.insert(log3TieBreakA)
        newsLogDao.insert(logNullTime)
        newsLogDao.insert(logUploaded)

        val pending = newsLogDao.getPendingUploads()

        // logUploaded should be excluded because _id is set to "remote_id_123"
        // Expected order:
        // 1. log2 (time = 500L)
        // 2. log3TieBreakA (time = 800L, id = "log_a_tie") [tiebreak id ASC]
        // 3. log3TieBreakB (time = 800L, id = "log_b")     [tiebreak id ASC]
        // 4. log1 (time = 1000L)
        // 5. logNullTime (time = null)                    [null time last]
        assertEquals(5, pending.size)
        assertEquals("log_a", pending[0].id)
        assertEquals("log_a_tie", pending[1].id)
        assertEquals("log_b", pending[2].id)
        assertEquals("log_c", pending[3].id)
        assertEquals("log_null_time", pending[4].id)
    }
}
