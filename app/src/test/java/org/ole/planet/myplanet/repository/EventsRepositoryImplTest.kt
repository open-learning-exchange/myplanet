package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser

@OptIn(ExperimentalCoroutinesApi::class)
class EventsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
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
        val timeProvider = org.ole.planet.myplanet.utils.SystemTimeProvider()
        repository = EventsRepositoryImpl(databaseService, UnconfinedTestDispatcher(), timeProvider)
    }

    @Test
    fun getMeetupsForTeam() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMeetup>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("teamId", "team1") } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns listOf(RealmMeetup().apply { id = "1" })

        val operationSlot = slot<Function1<Realm, List<RealmMeetup>>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getMeetupsForTeam("team1")

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun getMeetupById() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockMeetup = RealmMeetup().apply { meetupId = "meetup1" }
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns mockMeetup
        every { mockRealm.copyFromRealm(mockMeetup) } returns mockMeetup

        val operationSlot = slot<Function1<Realm, RealmMeetup?>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val result = repository.getMeetupById("meetup1")
        assertNotNull(result)
        assertEquals("meetup1", result?.meetupId)

        val emptyResult = repository.getMeetupById("")
        assertNull(emptyResult)
    }

    @Test
    fun getJoinedMembers() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
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
        val mockRealm = mockk<Realm>(relaxed = true)
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

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }
        val operationSlot = slot<Function1<Realm, RealmMeetup?>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

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
        val mockRealm = mockk<Realm>(relaxed = true)
        mockkObject(RealmMeetup.Companion)
        val docs = listOf(JsonObject(), JsonObject())

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }
        every { RealmMeetup.insertList(any(), any(), any()) } just Runs

        val count = repository.batchInsertMeetups(docs)
        assertEquals(2, count)

        verify(exactly = 1) { RealmMeetup.insertList(mockRealm, "", docs) }
        unmockkObject(RealmMeetup.Companion)
    }

    @Test
    fun batchInsertMeetupsInnerException() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        mockkObject(RealmMeetup.Companion)
        val docs = listOf(JsonObject(), JsonObject())

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        // Fail during insertList
        every { RealmMeetup.insertList(mockRealm, "", docs) } throws SilentException("Inner Exception")

        val count = repository.batchInsertMeetups(docs)
        assertEquals(0, count)

        verify(exactly = 1) { RealmMeetup.insertList(mockRealm, "", docs) }
        unmockkObject(RealmMeetup.Companion)
    }

    @Test
    fun batchInsertMeetupsException() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        mockkObject(RealmMeetup.Companion)
        val docs = listOf(JsonObject(), JsonObject())

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } throws SilentException("Outer Exception")

        val count = repository.batchInsertMeetups(docs)
        assertEquals(0, count)

        verify(exactly = 0) { RealmMeetup.insertList(any(), any(), any()) }
        unmockkObject(RealmMeetup.Companion)
    }

    @Test
    fun createMeetup() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        val params = org.ole.planet.myplanet.model.MeetupCreationParams(
            "title", "link", "desc", "loc", "start", "end", null, "planet", "user", 1L, 2L, "teamId"
        )
        every { mockRealm.copyToRealmOrUpdate(any<RealmMeetup>()) } returns RealmMeetup()

        val result = repository.createMeetup(params)
        assertTrue(result)
        verify { mockRealm.copyToRealmOrUpdate(any<RealmMeetup>()) }
    }

    @Test
    fun createMeetupException() = runTest {
        val mockRealm = mockk<Realm>(relaxed = true)
        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } throws SilentException("Test Exception")

        val params = org.ole.planet.myplanet.model.MeetupCreationParams(
            "title", "link", "desc", "loc", "start", "end", null, "planet", "user", 1L, 2L, "teamId"
        )

        val result = repository.createMeetup(params)
        assertFalse(result)
    }
}
