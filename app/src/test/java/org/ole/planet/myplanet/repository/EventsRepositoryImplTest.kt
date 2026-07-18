package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomUserEntity
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.utils.SystemTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class EventsRepositoryImplTest {

    private lateinit var meetupDao: MeetupDao
    private lateinit var userDao: UserDao
    private lateinit var repository: EventsRepositoryImpl

    class SilentException(message: String) : Exception(message) {
        override fun printStackTrace() = Unit
    }

    @Before
    fun setup() {
        meetupDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        repository = EventsRepositoryImpl(SystemTimeProvider(), meetupDao, userDao)
    }

    @Test
    fun getMeetupsForTeam() = runTest {
        coEvery { meetupDao.getByTeamId("team1") } returns listOf(Meetup().apply { id = "1" })

        val result = repository.getMeetupsForTeam("team1")

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun getMeetupById() = runTest {
        val mockMeetup = Meetup().apply { meetupId = "meetup1" }
        coEvery { meetupDao.getByMeetupId("meetup1") } returns mockMeetup

        val result = repository.getMeetupById("meetup1")
        assertNotNull(result)
        assertEquals("meetup1", result?.meetupId)

        val emptyResult = repository.getMeetupById("")
        assertNull(emptyResult)
    }

    @Test
    fun getJoinedMembers() = runTest {
        coEvery { meetupDao.getMembersByMeetupId("meetup1") } returns listOf(
            Meetup().apply { userId = "user1" },
            Meetup().apply { userId = "user2" },
            Meetup().apply { userId = "user1" }
        )
        coEvery { userDao.getAll() } returns listOf(
            RoomUserEntity(id = "user1"),
            RoomUserEntity(id = "user2", _id = "remote-user2"),
            RoomUserEntity(id = "user3")
        )

        val result = repository.getJoinedMembers("meetup1")

        assertEquals(2, result.size)
        assertEquals("user1", result[0].id)
        assertEquals("user2", result[1].id)

        val emptyResult = repository.getJoinedMembers("")
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun toggleAttendance() = runTest {
        val meetup = Meetup().apply { meetupId = "meetup1" }
        coEvery { meetupDao.getByMeetupId("meetup1") } returns meetup

        meetup.userId = ""
        val joinResult = repository.toggleAttendance("meetup1", "user1")
        assertEquals("user1", meetup.userId)
        assertNotNull(joinResult)

        meetup.userId = "user1"
        val leaveResult = repository.toggleAttendance("meetup1", "user1")
        assertEquals("", meetup.userId)
        assertNotNull(leaveResult)

        coVerify(atLeast = 1) { meetupDao.upsert(meetup) }

        val emptyResult = repository.toggleAttendance("", "user1")
        assertNull(emptyResult)
    }

    @Test
    fun batchInsertMeetups() = runTest {
        val docs = listOf(
            JsonObject().apply { addProperty("_id", "m1") },
            JsonObject().apply { addProperty("_id", "m2") }
        )
        coEvery { meetupDao.getByMeetupIds(any()) } returns emptyList()

        val count = repository.batchInsertMeetups(docs)
        assertEquals(2, count)

        val slot = slot<List<Meetup>>()
        coVerify(exactly = 1) { meetupDao.upsertAll(capture(slot)) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun batchInsertMeetupsSkipsLocallyUpdated() = runTest {
        val docs = listOf(JsonObject().apply { addProperty("_id", "m1") })
        coEvery { meetupDao.getByMeetupIds(any()) } returns listOf(
            Meetup().apply { meetupId = "m1"; updated = true }
        )

        val count = repository.batchInsertMeetups(docs)
        assertEquals(1, count)

        coVerify(exactly = 0) { meetupDao.upsertAll(any()) }
    }

    @Test
    fun batchInsertMeetupsException() = runTest {
        val docs = listOf(JsonObject().apply { addProperty("_id", "m1") })
        coEvery { meetupDao.getByMeetupIds(any()) } throws SilentException("boom")

        val count = repository.batchInsertMeetups(docs)
        assertEquals(0, count)
    }

    @Test
    fun createMeetup() = runTest {
        val params = MeetupCreationParams(
            "title", "link", "desc", "loc", "start", "end", null, "planet", "user", 1L, 2L, "teamId"
        )

        val result = repository.createMeetup(params)
        assertTrue(result)
        coVerify { meetupDao.upsert(any()) }
    }

    @Test
    fun createMeetupException() = runTest {
        coEvery { meetupDao.upsert(any()) } throws SilentException("Test Exception")

        val params = MeetupCreationParams(
            "title", "link", "desc", "loc", "start", "end", null, "planet", "user", 1L, 2L, "teamId"
        )

        val result = repository.createMeetup(params)
        assertFalse(result)
    }
}
