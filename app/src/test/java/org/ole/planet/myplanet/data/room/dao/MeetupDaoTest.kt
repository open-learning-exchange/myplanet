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
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.UserEntity

@RunWith(AndroidJUnit4::class)
class MeetupDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var meetupDao: MeetupDao
    private lateinit var userDao: UserDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        meetupDao = database.meetupDao()
        userDao = database.userDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createUser(id: String, _id: String? = null, name: String? = null): UserEntity {
        return UserEntity().apply {
            this.id = id
            this._id = _id
            this.name = name
        }
    }

    private fun createMeetup(id: String, meetupId: String, userId: String?): Meetup {
        return Meetup().apply {
            this.id = id
            this.meetupId = meetupId
            this.userId = userId
        }
    }

    @Test
    fun getJoinedMembersByMeetupId_matchesUserByIdOr_Id() = runBlocking {
        userDao.upsert(createUser("user1_local", "user1_remote", "User 1"))
        userDao.upsert(createUser("user2_local", null, "User 2"))

        meetupDao.upsert(createMeetup("m1", "meetup1", "user1_remote"))
        meetupDao.upsert(createMeetup("m2", "meetup1", "user2_local"))

        val result = meetupDao.getJoinedMembersByMeetupId("meetup1")
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "user1_local" })
        assertTrue(result.any { it.id == "user2_local" })
    }

    @Test
    fun getJoinedMembersByMeetupId_deduplicatesMembers() = runBlocking {
        userDao.upsert(createUser("user1_local", "user1_remote", "User 1"))

        meetupDao.upsert(createMeetup("m1", "meetup1", "user1_local"))
        meetupDao.upsert(createMeetup("m2", "meetup1", "user1_remote"))

        val result = meetupDao.getJoinedMembersByMeetupId("meetup1")
        assertEquals(1, result.size)
        assertEquals("user1_local", result[0].id)
    }

    @Test
    fun getJoinedMembersByMeetupId_excludesNullOrBlankUserIds() = runBlocking {
        userDao.upsert(createUser("user1_local", "user1_remote", "User 1"))

        meetupDao.upsert(createMeetup("m1", "meetup1", "user1_local"))
        meetupDao.upsert(createMeetup("m2", "meetup1", null))
        meetupDao.upsert(createMeetup("m3", "meetup1", ""))

        val result = meetupDao.getJoinedMembersByMeetupId("meetup1")
        assertEquals(1, result.size)
        assertEquals("user1_local", result[0].id)
    }

    @Test
    fun getJoinedMembersByMeetupId_returnsEmptyWhenNoMembersOrBlankMeetupId() = runBlocking {
        userDao.upsert(createUser("user1_local", "user1_remote", "User 1"))
        meetupDao.upsert(createMeetup("m1", "meetup1", "user1_local"))

        val resultUnknown = meetupDao.getJoinedMembersByMeetupId("unknown_meetup")
        assertTrue(resultUnknown.isEmpty())

        val resultBlank = meetupDao.getJoinedMembersByMeetupId("")
        assertTrue(resultBlank.isEmpty())
    }
}
