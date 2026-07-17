package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.SystemTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class EventsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var meetupDao: MeetupDao
    private lateinit var repository: EventsRepositoryImpl

    // A silent exception to avoid cluttering test logs with stack traces
    class SilentException(message: String) : Exception(message) {
        override fun printStackTrace() {
            // Do nothing
        }
    }

    @Before
    fun setup() {
        databaseService = mockk(relaxed = true)
        meetupDao = mockk(relaxed = true)
        val timeProvider = SystemTimeProvider()
        repository = EventsRepositoryImpl(databaseService, UnconfinedTestDispatcher(), timeProvider, meetupDao)
    }

    @Test
    fun getMeetupsForTeam() = runTest {
        coEvery { meetupDao.getByTeamId("team1") } returns listOf(RealmMeetup().apply { id = "1" })

        val result = repository.getMeetupsForTeam("team1")

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun getMeetupById() = runTest {
        val mockMeetup = RealmMeetup().apply { meetupId = "meetup1" }
        coEvery { meetupDao.getByMeetupId("meetup1") } returns mockMeetup

        val result = repository.getMeetupById("meetup1")
        assertNotNull(result)
        assertEquals("meetup1", result?.meetupId)

        val emptyResult = repository.getMeetupById("")
        assertNull(emptyResult)
    }

    @Test
    fun getJoinedMembers() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockUserResults = mockk<RealmResults<RealmUser>>(relaxed = true)
        val mockUserQuery = mockk<RealmQuery<RealmUser>>(relaxed = true)

        coEvery { meetupDao.getMembersByMeetupId("meetup1") } returns listOf(
            RealmMeetup().apply { userId = "user1" },
            RealmMeetup().apply { userId = "user2" },
            RealmMeetup().apply { userId = "user1" } // duplicate
        )

        every { mockRealm.where(RealmUser::class.java) } returns mockUserQuery
        every { mockUserQuery.`in`("id", arrayOf("user1", "user2")) } returns mockUserQuery
        every { mockUserQuery.findAll() } returns mockUserResults
        every { mockRealm.copyFromRealm(mockUserResults) } returns listOf(RealmUser().apply { id = "user1" }, RealmUser().apply { id = "user2" })

        val operationSlot = slot<Function1<Realm, List<RealmUser>>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getJoinedMembers("meetup1")

        assertEquals(2, result.size)
        assertEquals("user1", result[0].id)
        assertEquals("user2", result[1].id)

        val emptyResult = repository.getJoinedMembers("")
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun toggleAttendance() = runTest {
        val meetup = RealmMeetup().apply { meetupId = "meetup1" }
        coEvery { meetupDao.getByMeetupId("meetup1") } returns meetup

        // Test joining (userId empty -> currentUserId)
        meetup.userId = ""
        val joinResult = repository.toggleAttendance("meetup1", "user1")
        assertEquals("user1", meetup.userId)
        assertNotNull(joinResult)

        // Test leaving (userId set -> empty)
        meetup.userId = "user1"
        val leaveResult = repository.toggleAttendance("meetup1", "user1")
        assertEquals("", meetup.userId)
        assertNotNull(leaveResult)

        coVerify(atLeast = 1) { meetupDao.upsert(meetup) }

        // Test empty id
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

        val slot = slot<List<RealmMeetup>>()
        coVerify(exactly = 1) { meetupDao.upsertAll(capture(slot)) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun batchInsertMeetupsSkipsLocallyUpdated() = runTest {
        val docs = listOf(JsonObject().apply { addProperty("_id", "m1") })
        coEvery { meetupDao.getByMeetupIds(any()) } returns listOf(
            RealmMeetup().apply { meetupId = "m1"; updated = true }
        )

        val count = repository.batchInsertMeetups(docs)
        assertEquals(1, count)

        // The only doc is locally-updated, so nothing is upserted.
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
