package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.services.UserSessionManager

@RunWith(AndroidJUnit4::class)
class OfflineActivityDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var offlineActivityDao: OfflineActivityDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        offlineActivityDao = database.offlineActivityDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createActivity(
        id: String,
        userId: String,
        userName: String,
        type: String,
        loginTime: Long
    ) = OfflineActivity().apply {
        this.id = id
        this.userId = userId
        this.userName = userName
        this.type = type
        this.loginTime = loginTime
    }

    @Test
    fun getLastVisits_groupsByUserNameAndReturnsMaxLoginTime() = runBlocking {
        offlineActivityDao.insert(createActivity("1", "u1", "alice", "login", 1000L))
        offlineActivityDao.insert(createActivity("2", "u1", "alice", "other", 5000L))
        offlineActivityDao.insert(createActivity("3", "u2", "bob", "login", 2000L))

        val results = offlineActivityDao.getLastVisits(listOf("alice", "bob", "charlie_no_rows"))

        val map = results.mapNotNull {
            val name = it.userName ?: return@mapNotNull null
            val time = it.lastVisit ?: return@mapNotNull null
            name to time
        }.toMap()

        assertEquals(2, map.size)
        assertEquals(5000L, map["alice"])
        assertEquals(2000L, map["bob"])
        assertTrue("charlie_no_rows" !in map)
    }

    @Test
    fun countByUserIdsAndType_countsMatchingTypeGroupedByUserId() = runBlocking {
        offlineActivityDao.insert(createActivity("1", "u1", "alice", UserSessionManager.KEY_LOGIN, 1000L))
        offlineActivityDao.insert(createActivity("2", "u1", "alice", UserSessionManager.KEY_LOGIN, 2000L))
        offlineActivityDao.insert(createActivity("3", "u1", "alice", "other_type", 3000L))
        offlineActivityDao.insert(createActivity("4", "u2", "bob", UserSessionManager.KEY_LOGIN, 4000L))

        val results = offlineActivityDao.countByUserIdsAndType(
            listOf("u1", "u2", "u3_no_rows"),
            UserSessionManager.KEY_LOGIN
        )

        val map = results.mapNotNull {
            val id = it.userId ?: return@mapNotNull null
            id to it.count
        }.toMap()

        assertEquals(2, map.size)
        assertEquals(2, map["u1"])
        assertEquals(1, map["u2"])
        assertTrue("u3_no_rows" !in map)
    }

    @Test
    fun chunkedQueries_handlesEmptyListsAndLargeLists() = runBlocking {
        assertTrue(offlineActivityDao.getLastVisits(emptyList()).isEmpty())
        assertTrue(offlineActivityDao.countByUserIdsAndType(emptyList(), UserSessionManager.KEY_LOGIN).isEmpty())

        val manyUserNames = (1..950).map { "user_$it" }
        val manyUserIds = (1..950).map { "id_$it" }

        offlineActivityDao.insert(createActivity("act1", "id_920", "user_920", UserSessionManager.KEY_LOGIN, 12345L))

        val visits = offlineActivityDao.getLastVisits(manyUserNames).associate { it.userName to it.lastVisit }
        val counts = offlineActivityDao.countByUserIdsAndType(manyUserIds, UserSessionManager.KEY_LOGIN).associate { it.userId to it.count }

        assertEquals(12345L, visits["user_920"])
        assertEquals(1, counts["id_920"])
    }
}
