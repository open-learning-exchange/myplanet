package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmMeetup
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CommunityRepositoryImplTest {

    @MockK
    lateinit var mockDatabaseService: DatabaseService

    @MockK
    lateinit var mockApiInterface: ApiInterface

    @MockK
    lateinit var mockRealm: Realm

    lateinit var repository: CommunityRepositoryImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { mockDatabaseService.withRealm<Any>(any()) } answers {
            val block = arg<(Realm) -> Any>(0)
            block(mockRealm)
        }
        io.mockk.coEvery { mockDatabaseService.withRealmAsync<Any>(any()) } answers {
            val block = arg<(Realm) -> Any>(0)
            block(mockRealm)
        }
        repository = CommunityRepositoryImpl(mockDatabaseService, Dispatchers.Unconfined, mockApiInterface)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `insertMeetup with new meetup creates and sets properties`() {
        val userId = "user123"
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
            addProperty("_rev", "rev1")
            addProperty("title", "Test Meetup")
            addProperty("description", "Test Description")
            addProperty("startDate", 1600000000000)
            addProperty("endDate", 1600003600000)
            addProperty("recurring", "weekly")
            addProperty("startTime", "10:00")
            addProperty("endTime", "11:00")
            addProperty("category", "tech")
            addProperty("meetupLocation", "Room 1")
            addProperty("meetupLink", "http://meetup.com")
            addProperty("createdBy", "creator1")

            val dayArray = JsonArray().apply { add("Monday") }
            add("day", dayArray)

            val linkObj = JsonObject().apply { addProperty("teams", "team1") }
            add("link", linkObj)
        }

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val newMeetup = mockk<RealmMeetup>(relaxed = true)
        every { mockRealm.createObject(RealmMeetup::class.java, "meetup1") } returns newMeetup

        repository.insertMeetup(userId, mockRealm, meetupDoc)

        verify { newMeetup.meetupId = "meetup1" }
        verify { newMeetup.userId = userId }
        verify { newMeetup.meetupIdRev = "rev1" }
        verify { newMeetup.title = "Test Meetup" }
        verify { newMeetup.description = "Test Description" }
        verify { newMeetup.startDate = 1600000000000 }
        verify { newMeetup.endDate = 1600003600000 }
        verify { newMeetup.recurring = "weekly" }
        verify { newMeetup.startTime = "10:00" }
        verify { newMeetup.endTime = "11:00" }
        verify { newMeetup.category = "tech" }
        verify { newMeetup.meetupLocation = "Room 1" }
        verify { newMeetup.meetupLink = "http://meetup.com" }
        verify { newMeetup.creator = "creator1" }
        verify { newMeetup.day = """["Monday"]""" }
        verify { newMeetup.link = """{"teams":"team1"}""" }
        verify { newMeetup.teamId = "team1" }
    }

    @Test
    fun `insertMeetup with existing meetup updates properties`() {
        val userId = "user123"
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
            addProperty("_rev", "rev2")
            addProperty("title", "Updated Meetup")
        }

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery

        val existingMeetup = mockk<RealmMeetup>(relaxed = true)
        every { mockQuery.findFirst() } returns existingMeetup

        repository.insertMeetup(userId, mockRealm, meetupDoc)

        verify(exactly = 0) { mockRealm.createObject(RealmMeetup::class.java, "meetup1") }
        verify { existingMeetup.meetupId = "meetup1" }
        verify { existingMeetup.userId = userId }
        verify { existingMeetup.meetupIdRev = "rev2" }
        verify { existingMeetup.title = "Updated Meetup" }
    }

    @Test
    fun `insertMeetup with empty userId uses passed empty string`() {
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
        }

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val newMeetup = mockk<RealmMeetup>(relaxed = true)
        every { mockRealm.createObject(RealmMeetup::class.java, "meetup1") } returns newMeetup

        repository.insertMeetup("", mockRealm, meetupDoc)

        verify { newMeetup.userId = "" }
    }

    @Test
    fun `getMyMeetupIds returns json array of ids`() = runTest {
        val userId = "user123"

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.isNotEmpty("userId") } returns mockQuery
        every { mockQuery.equalTo("userId", userId, Case.INSENSITIVE) } returns mockQuery

        val meetup1 = mockk<RealmMeetup> { every { meetupId } returns "id1" }
        val meetup2 = mockk<RealmMeetup> { every { meetupId } returns "id2" }
        val mockResults = mockk<RealmResults<RealmMeetup>>()
        every { mockResults.iterator() } returns mutableListOf(meetup1, meetup2).iterator()
        every { mockQuery.findAll() } returns mockResults

        val result = repository.getMyMeetupIds(userId)

        assertEquals(2, result.size())
        assertEquals("id1", result[0].asString)
        assertEquals("id2", result[1].asString)
    }

    @Test
    fun `getMyMeetupIds with null realm returns empty array`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.isNotEmpty("userId") } returns mockQuery
        every { mockQuery.equalTo("userId", "user123", Case.INSENSITIVE) } returns mockQuery

        val mockResults = mockk<RealmResults<RealmMeetup>>()
        every { mockResults.iterator() } returns mutableListOf<RealmMeetup>().iterator()
        every { mockQuery.findAll() } returns mockResults

        val result = repository.getMyMeetupIds("user123")
        assertEquals(0, result.size())
    }
}
