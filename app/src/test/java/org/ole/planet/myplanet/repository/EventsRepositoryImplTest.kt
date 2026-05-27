package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.*
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser

@OptIn(ExperimentalCoroutinesApi::class)
class EventsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: EventsRepositoryImpl
    private lateinit var mockRealm: Realm

    // A silent exception to avoid cluttering test logs with stack traces
    class SilentException(message: String) : Exception(message) {
        override fun printStackTrace() {
            // Do nothing
        }
    }

    @Before
    fun setup() {
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val operation = firstArg<(Realm) -> Any>()
            operation(mockRealm)
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(mockRealm)
        }

        repository = EventsRepositoryImpl(databaseService, UnconfinedTestDispatcher())
    }

    @Test
    fun getMeetupsForTeam() = runTest {
        val mockResults = mockk<RealmResults<RealmMeetup>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("teamId", "team1") } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns listOf(RealmMeetup().apply { id = "1" })

        val result = repository.getMeetupsForTeam("team1")

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun getMeetupById() = runTest {
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockMeetup = RealmMeetup().apply { meetupId = "meetup1" }
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns mockMeetup
        every { mockRealm.copyFromRealm(mockMeetup) } returns mockMeetup

        val result = repository.getMeetupById("meetup1")
        assertNotNull(result)
        assertEquals("meetup1", result?.meetupId)

        val emptyResult = repository.getMeetupById("")
        assertNull(emptyResult)
    }

    @Test
    fun getJoinedMembers() = runTest {
        val mockResults = mockk<RealmResults<RealmMeetup>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockUserResults = mockk<RealmResults<RealmUser>>(relaxed = true)
        val mockUserQuery = mockk<RealmQuery<RealmUser>>(relaxed = true)

        val meetupMember1 = mockk<RealmMeetup>(relaxed = true)
        every { meetupMember1.userId } returns "user1"
        val meetupMember2 = mockk<RealmMeetup>(relaxed = true)
        every { meetupMember2.userId } returns "user2"
        val meetupMember3 = mockk<RealmMeetup>(relaxed = true)
        every { meetupMember3.userId } returns "user1" // duplicate

        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.isNotEmpty("userId") } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns listOf(meetupMember1, meetupMember2, meetupMember3)

        every { mockRealm.where(RealmUser::class.java) } returns mockUserQuery
        every { mockUserQuery.`in`("id", arrayOf("user1", "user2")) } returns mockUserQuery
        every { mockUserQuery.findAll() } returns mockUserResults
        every { mockRealm.copyFromRealm(mockUserResults) } returns listOf(RealmUser().apply { id = "user1" }, RealmUser().apply { id = "user2" })

        val result = repository.getJoinedMembers("meetup1")

        assertEquals(2, result.size)
        assertEquals("user1", result[0].id)
        assertEquals("user2", result[1].id)

        val emptyResult = repository.getJoinedMembers("")
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun toggleAttendance() = runTest {
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockMeetup = mockk<RealmMeetup>(relaxed = true)
        val mockMeetupQueryFirst = mockk<RealmMeetup>(relaxed = true)

        // Setup for update
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns mockMeetupQueryFirst
        every { mockRealm.copyFromRealm(mockMeetupQueryFirst) } returns mockMeetupQueryFirst

        // Setup for getMeetupById
        every { mockRealm.copyFromRealm(mockMeetup) } returns mockMeetup

        // Test joining
        every { mockMeetupQueryFirst.userId } returns ""
        val joinResult = repository.toggleAttendance("meetup1", "user1")
        verify { mockMeetupQueryFirst.userId = "user1" }
        assertNotNull(joinResult)

        // Test leaving
        every { mockMeetupQueryFirst.userId } returns "user1"
        val leaveResult = repository.toggleAttendance("meetup1", "user1")
        verify { mockMeetupQueryFirst.userId = "" }
        assertNotNull(leaveResult)

        // Test empty id
        val emptyResult = repository.toggleAttendance("", "user1")
        assertNull(emptyResult)
    }

    @Test
    fun batchInsertMeetups() = runTest {
        mockkObject(RealmMeetup.Companion)
        val docs = listOf(JsonObject(), JsonObject())

        every { RealmMeetup.insert(any(), any()) } just Runs

        val count = repository.batchInsertMeetups(docs)
        assertEquals(2, count)

        verify(exactly = 2) { RealmMeetup.insert(mockRealm, any()) }
        unmockkObject(RealmMeetup.Companion)
    }

    @Test
    fun batchInsertMeetupsException() = runTest {
        mockkObject(RealmMeetup.Companion)
        val docs = listOf(JsonObject(), JsonObject())

        every { RealmMeetup.insert(any(), any()) } throws SilentException("Test Exception")

        val count = repository.batchInsertMeetups(docs)
        assertEquals(0, count)

        verify(exactly = 2) { RealmMeetup.insert(mockRealm, any()) }
        unmockkObject(RealmMeetup.Companion)
    }

    @Test
    fun createMeetup() = runTest {
        val meetup = RealmMeetup()
        every { mockRealm.copyToRealmOrUpdate(meetup) } returns meetup

        val result = repository.createMeetup(meetup)
        assertTrue(result)
        verify { mockRealm.copyToRealmOrUpdate(meetup) }
    }

    @Test
    fun createMeetupException() = runTest {
        val meetup = RealmMeetup()
        every { mockRealm.copyToRealmOrUpdate(meetup) } throws SilentException("Test Exception")

        val result = repository.createMeetup(meetup)
        assertFalse(result)
        verify { mockRealm.copyToRealmOrUpdate(meetup) }
    }
}
