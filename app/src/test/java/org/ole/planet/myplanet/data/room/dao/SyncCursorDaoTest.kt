package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.SyncCursor
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32])
class SyncCursorDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var syncCursorDao: SyncCursorDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        syncCursorDao = database.syncCursorDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getSince_withNoCursor_returnsNull() = runBlocking {
        assertNull(syncCursorDao.getSince("ratings"))
    }

    @Test
    fun upsert_thenGetSince_returnsPersistedValue() = runBlocking {
        syncCursorDao.upsert(SyncCursor("ratings", "42"))

        assertEquals("42", syncCursorDao.getSince("ratings"))
    }

    @Test
    fun upsert_onExistingTable_replacesThePreviousCursor() = runBlocking {
        syncCursorDao.upsert(SyncCursor("ratings", "42"))
        syncCursorDao.upsert(SyncCursor("ratings", "99"))

        assertEquals("99", syncCursorDao.getSince("ratings"))
    }

    @Test
    fun cursorsForDifferentTables_areIndependent() = runBlocking {
        syncCursorDao.upsert(SyncCursor("ratings", "42"))
        syncCursorDao.upsert(SyncCursor("submissions", "7"))

        assertEquals("42", syncCursorDao.getSince("ratings"))
        assertEquals("7", syncCursorDao.getSince("submissions"))
    }

    @Test
    fun clear_removesTheCursor() = runBlocking {
        syncCursorDao.upsert(SyncCursor("ratings", "42"))

        syncCursorDao.clear("ratings")

        assertNull(syncCursorDao.getSince("ratings"))
    }
}
