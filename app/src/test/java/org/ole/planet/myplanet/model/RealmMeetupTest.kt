package org.ole.planet.myplanet.model

import android.app.Application
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.TimeUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RealmMeetupTest {

    @MockK
    lateinit var mockRealm: Realm

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `insert with new meetup creates and sets properties`() {
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

            val daysArray = JsonArray()
            daysArray.add("Monday")
            add("day", daysArray)

            val linkObj = JsonObject()
            linkObj.addProperty("teams", "team1")
            add("link", linkObj)
        }

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.insertOrUpdate(any<RealmMeetup>()) } returns Unit

        RealmMeetup.insert(userId, meetupDoc, mockRealm)

        verify {
            mockRealm.insertOrUpdate(withArg<RealmMeetup> {
                assertEquals("meetup1", it.meetupId)
                assertEquals(userId, it.userId)
                assertEquals("rev1", it.meetupIdRev)
                assertEquals("Test Meetup", it.title)
                assertEquals("Test Description", it.description)
                assertEquals(1600000000000, it.startDate)
                assertEquals(1600003600000, it.endDate)
                assertEquals("weekly", it.recurring)
                assertEquals("10:00", it.startTime)
                assertEquals("11:00", it.endTime)
                assertEquals("tech", it.category)
                assertEquals("Room 1", it.meetupLocation)
                assertEquals("http://meetup.com", it.meetupLink)
                assertEquals("creator1", it.creator)
                assertEquals("""["Monday"]""", it.day)
                assertEquals("""{"teams":"team1"}""", it.link)
                assertEquals("team1", it.teamId)
            })
        }
    }

    @Test
    fun `insert with existing meetup updates properties`() {
        val userId = "user123"
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
            addProperty("_rev", "rev2")
            addProperty("title", "Updated Meetup")
        }

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery

        val existingMeetup = RealmMeetup()
        existingMeetup.createdDate = 12345L
        existingMeetup.sync = "synced"
        every { mockQuery.findFirst() } returns existingMeetup
        every { mockRealm.insertOrUpdate(any<RealmMeetup>()) } returns Unit

        RealmMeetup.insert(userId, meetupDoc, mockRealm)

        verify {
            mockRealm.insertOrUpdate(withArg<RealmMeetup> {
                assertEquals("meetup1", it.meetupId)
                assertEquals(userId, it.userId)
                assertEquals("rev2", it.meetupIdRev)
                assertEquals("Updated Meetup", it.title)
                assertEquals(12345L, it.createdDate)
                assertEquals("synced", it.sync)
            })
        }
    }

    @Test
    fun `insert without userId uses empty string`() {
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
        }

        val mockQuery = mockk<RealmQuery<RealmMeetup>>()
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.insertOrUpdate(any<RealmMeetup>()) } returns Unit

        RealmMeetup.insert(mockRealm, meetupDoc)

        verify {
            mockRealm.insertOrUpdate(withArg<RealmMeetup> {
                assertEquals("", it.userId)
            })
        }
    }

    @Test
    fun `getMyMeetUpIds returns json array of ids`() {
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

        val result = RealmMeetup.getMyMeetUpIds(mockRealm, userId)

        assertEquals(2, result.size())
        assertEquals("id1", result[0].asString)
        assertEquals("id2", result[1].asString)
    }

    @Test
    fun `getMyMeetUpIds with null realm returns empty array`() {
        val result = RealmMeetup.getMyMeetUpIds(null, "user123")
        assertEquals(0, result.size())
    }

    @Test
    fun `getHashMap extracts correct map values`() {
        val meetup = mockk<RealmMeetup>()
        every { meetup.title } returns "Test Title"
        every { meetup.creator } returns "Test Creator"
        every { meetup.category } returns "Tech"
        every { meetup.startDate } returns 1600000000000 // A valid timestamp
        every { meetup.endDate } returns 1600003600000
        every { meetup.startTime } returns "10:00"
        every { meetup.endTime } returns "11:00"
        every { meetup.recurring } returns "weekly"
        every { meetup.day } returns """["Monday", "Wednesday"]"""
        every { meetup.meetupLocation } returns "Room A"
        every { meetup.meetupLink } returns "http://meetup.link"
        every { meetup.description } returns "Test Description"

        val expectedStartDate = TimeUtils.getFormattedDate(1600000000000)
        val expectedEndDate = TimeUtils.getFormattedDate(1600003600000)

        val map = RealmMeetup.getHashMap(meetup)

        assertEquals("Test Title", map["Meetup Title"])
        assertEquals("Test Creator", map["Created By"])
        assertEquals("Tech", map["Category"])
        assertEquals("$expectedStartDate - $expectedEndDate", map["Meetup Date"])
        assertEquals("10:00 - 11:00", map["Meetup Time"])
        assertEquals("weekly", map["Recurring"])
        assertEquals("Monday, Wednesday, ", map["Recurring Days"])
        assertEquals("Room A", map["Location"])
        assertEquals("http://meetup.link", map["Link"])
        assertEquals("Test Description", map["Description"])
    }

    @Test
    fun `getHashMap handles null values properly`() {
        val meetup = mockk<RealmMeetup>(relaxed = true)
        every { meetup.title } returns null
        every { meetup.creator } returns null
        every { meetup.day } returns "[]" // valid empty array to avoid JSONException console pollution

        val map = RealmMeetup.getHashMap(meetup)

        assertEquals("", map["Meetup Title"])
        assertEquals("", map["Created By"])
        assertEquals("", map["Recurring Days"])
    }

    @Test
    fun `serialize creates correct JsonObject`() {
        val meetup = RealmMeetup().apply {
            meetupId = "meetup1"
            meetupIdRev = "rev1"
            title = "Test Meetup"
            description = "Desc"
            startDate = 100L
            endDate = 200L
            startTime = "10:00"
            endTime = "11:00"
            recurring = "weekly"
            meetupLocation = "Loc"
            meetupLink = "Link"
            creator = "Creator"
            teamId = "Team1"
            category = "Cat"
            createdDate = 50L
            recurringNumber = 5
            sourcePlanet = "Planet"
            sync = "true"
            link = """{"some":"data"}"""
        }

        val jsonObject = RealmMeetup.serialize(meetup)

        assertEquals("meetup1", jsonObject.get("_id").asString)
        assertEquals("rev1", jsonObject.get("_rev").asString)
        assertEquals("Test Meetup", jsonObject.get("title").asString)
        assertEquals("Desc", jsonObject.get("description").asString)
        assertEquals(100L, jsonObject.get("startDate").asLong)
        assertEquals(200L, jsonObject.get("endDate").asLong)
        assertEquals("10:00", jsonObject.get("startTime").asString)
        assertEquals("11:00", jsonObject.get("endTime").asString)
        assertEquals("weekly", jsonObject.get("recurring").asString)
        assertEquals("Loc", jsonObject.get("meetupLocation").asString)
        assertEquals("Link", jsonObject.get("meetupLink").asString)
        assertEquals("Creator", jsonObject.get("createdBy").asString)
        assertEquals("Team1", jsonObject.get("teamId").asString)
        assertEquals("Cat", jsonObject.get("category").asString)
        assertEquals(50L, jsonObject.get("createdDate").asLong)
        assertEquals(5, jsonObject.get("recurringNumber").asInt)
        assertEquals("Planet", jsonObject.get("sourcePlanet").asString)
        assertEquals("true", jsonObject.get("sync").asString)
        assertEquals("data", jsonObject.getAsJsonObject("link").get("some").asString)
    }

    @Test
    fun `serialize skips null or empty id and link`() {
        val meetup = RealmMeetup().apply {
            meetupId = ""
            meetupIdRev = null
            link = null
        }

        val jsonObject = RealmMeetup.serialize(meetup)

        assertEquals(false, jsonObject.has("_id"))
        assertEquals(false, jsonObject.has("_rev"))
        assertEquals(false, jsonObject.has("link"))
    }
}
