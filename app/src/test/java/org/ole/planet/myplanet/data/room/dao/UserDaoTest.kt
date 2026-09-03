package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.UserEntity
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class UserDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        userDao = database.userDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createUser(id: String, _id: String?, name: String?, isUpdated: Boolean = false): UserEntity {
        return UserEntity().apply {
            this.id = id
            this._id = _id
            this.name = name
            this.isUpdated = isUpdated
        }
    }

    @Test
    fun getUsersByAnyIds_matchesIdOr_Id() = runBlocking {
        userDao.upsert(createUser("local1", "remote1", "User 1"))
        userDao.upsert(createUser("local2", "remote2", "User 2"))

        val result = userDao.getUsersByAnyIds(listOf("local1", "remote2"))
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "local1" })
        assertTrue(result.any { it.id == "local2" })
    }

    @Test
    fun getSyncedUsers_excludesGuestsAndNullOrBlank_Id() = runBlocking {
        userDao.upsert(createUser("user1", "remote1", "Normal User"))
        userDao.upsert(createUser("guest123", "remote2", "Guest User")) // Excluded by id NOT LIKE 'guest%' (now SUBSTR)
        userDao.upsert(createUser("user2", null, "Unsynced")) // Excluded by _id IS NOT NULL
        userDao.upsert(createUser("user3", "  ", "Blank Id")) // Excluded by TRIM(_id) != ''
        userDao.upsert(createUser("GUEST123", "remote3", "Uppercase Guest")) // Should be INCLUDED since SUBSTR != 'guest' is case-sensitive

        val result = userDao.getSyncedUsers()
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "user1" })
        assertTrue(result.any { it.id == "GUEST123" })
    }

    @Test
    fun getUsersForHealthSync_excludesNullOrBlank_Id() = runBlocking {
        userDao.upsert(createUser("user1", "remote1", "Normal"))
        userDao.upsert(createUser("user2", null, "Null ID"))
        userDao.upsert(createUser("user3", "   ", "Blank ID"))

        val result = userDao.getUsersForHealthSync()
        assertEquals(1, result.size)
        assertEquals("user1", result[0].id)
    }

    @Test
    fun getPendingSyncUsers_matchesNullBlankOrUpdated() = runBlocking {
        userDao.upsert(createUser("user1", "remote1", "Synced", isUpdated = false))
        userDao.upsert(createUser("user2", null, "Pending Null", isUpdated = false))
        userDao.upsert(createUser("user3", "  ", "Pending Blank", isUpdated = false))
        userDao.upsert(createUser("user4", "remote4", "Pending Updated", isUpdated = true))

        val result = userDao.getPendingSyncUsers(10)
        assertEquals(3, result.size)
        assertTrue(result.any { it.id == "user2" })
        assertTrue(result.any { it.id == "user3" })
        assertTrue(result.any { it.id == "user4" })
    }

    @Test
    fun getPendingSyncUsers_ordersByIdWhenLimited() = runBlocking {
        userDao.upsert(createUser("user3", null, "Third"))
        userDao.upsert(createUser("user1", null, "First"))
        userDao.upsert(createUser("user2", null, "Second"))

        val result = userDao.getPendingSyncUsers(2)
        assertEquals(listOf("user1", "user2"), result.map { it.id })
    }

    @Test
    fun getGuestUserByName_matchesNameAndGuest_Id() = runBlocking {
        userDao.upsert(createUser("id1", "guest_userA", "userA"))
        userDao.upsert(createUser("id2", "guestuserB", "userB")) // Excluded, SUBSTR(_id, 1, 6) != 'guest_'
        userDao.upsert(createUser("id3", "remote3", "userA")) // Same name, not a guest

        val result = userDao.getGuestUserByName("userA")
        assertNotNull(result)
        assertEquals("id1", result!!.id)
    }

    @Test
    fun getDuplicateUsers_groupsByNullNamesCorrectly() = runBlocking {
        userDao.upsert(createUser("id1", "r1", "John"))
        userDao.upsert(createUser("id2", "r2", "John")) // Duplicate of John
        userDao.upsert(createUser("id3", "r3", "Jane"))
        userDao.upsert(createUser("id4", "r4", null))
        userDao.upsert(createUser("id5", "r5", null)) // Duplicate of null

        val result = userDao.getDuplicateUsers()
        assertEquals(4, result.size) // Both Johns and both Nulls
        assertEquals(2, result.count { it.name == "John" })
        assertEquals(2, result.count { it.name == null })
    }
}
