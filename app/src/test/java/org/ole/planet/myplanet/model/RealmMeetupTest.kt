package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.ole.planet.myplanet.utils.TimeUtils
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RealmMeetupTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockQuery: RealmQuery<RealmMeetup>

    @MockK
    lateinit var mockMeetup: RealmMeetup

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInsertNewMeetup() {
        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "meetup1") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmMeetup::class.java, "meetup1") } returns mockMeetup

        every { mockMeetup.meetupId = any() } just Runs
        every { mockMeetup.userId = any() } just Runs
        every { mockMeetup.meetupIdRev = any() } just Runs
        every { mockMeetup.title = any() } just Runs
        every { mockMeetup.description = any() } just Runs
        every { mockMeetup.startDate = any() } just Runs
        every { mockMeetup.endDate = any() } just Runs
        every { mockMeetup.recurring = any() } just Runs
        every { mockMeetup.startTime = any() } just Runs
        every { mockMeetup.endTime = any() } just Runs
        every { mockMeetup.category = any() } just Runs
        every { mockMeetup.meetupLocation = any() } just Runs
        every { mockMeetup.meetupLink = any() } just Runs
        every { mockMeetup.creator = any() } just Runs
        every { mockMeetup.day = any() } just Runs
        every { mockMeetup.link = any() } just Runs
        every { mockMeetup.teamId = any() } just Runs

        val jsonObject = JsonObject()
        jsonObject.addProperty("_id", "meetup1")
        jsonObject.addProperty("_rev", "rev1")
        jsonObject.addProperty("title", "Meetup Title")
        jsonObject.addProperty("description", "Description")
        jsonObject.addProperty("startDate", 1000L)
        jsonObject.addProperty("endDate", 2000L)
        jsonObject.addProperty("recurring", "daily")
        jsonObject.addProperty("startTime", "10:00")
        jsonObject.addProperty("endTime", "11:00")
        jsonObject.addProperty("category", "Tech")
        jsonObject.addProperty("meetupLocation", "Online")
        jsonObject.addProperty("meetupLink", "http://example.com")
        jsonObject.addProperty("createdBy", "creator1")

        val dayArray = com.google.gson.JsonArray()
        dayArray.add("Monday")
        jsonObject.add("day", dayArray)

        val linkObject = JsonObject()
        linkObject.addProperty("teams", "team1")
        jsonObject.add("link", linkObject)

        RealmMeetup.insert("user1", jsonObject, mockRealm)

        verify { mockRealm.createObject(RealmMeetup::class.java, "meetup1") }
        verify { mockMeetup.meetupId = "meetup1" }
        verify { mockMeetup.userId = "user1" }
        verify { mockMeetup.title = "Meetup Title" }
    }

    @Test
    fun testGetMyMeetUpIds() {
        val mockRealmResults = mockk<RealmResults<RealmMeetup>>()

        val meetup1 = mockk<RealmMeetup>()
        every { meetup1.meetupId } returns "meetup1"

        val meetup2 = mockk<RealmMeetup>()
        every { meetup2.meetupId } returns "meetup2"

        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.isNotEmpty("userId") } returns mockQuery
        every { mockQuery.equalTo("userId", "user1", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.findAll() } returns mockRealmResults

        val iterator = mutableListOf(meetup1, meetup2).listIterator()
        every { mockRealmResults.iterator() } returns iterator

        val ids = RealmMeetup.getMyMeetUpIds(mockRealm, "user1")
        assertEquals(2, ids.size())
        assertEquals("meetup1", ids[0].asString)
        assertEquals("meetup2", ids[1].asString)
    }

    @Test
    fun testGetHashMap() {
        mockkObject(TimeUtils)
        every { TimeUtils.getFormattedDate(any<Long>()) } returns "01-01-2023"

        val meetup = RealmMeetup()
        meetup.title = "Meetup Title"
        meetup.creator = "Creator"
        meetup.category = "Category"
        meetup.startDate = 1672531200000L
        meetup.endDate = 1672617600000L
        meetup.startTime = "10:00"
        meetup.endTime = "12:00"
        meetup.recurring = "Weekly"
        meetup.day = "[\"Monday\", \"Wednesday\"]"
        meetup.meetupLocation = "Location"
        meetup.meetupLink = "Link"
        meetup.description = "Description"

        val map = RealmMeetup.getHashMap(meetup)

        assertEquals("Meetup Title", map["Meetup Title"])
        assertEquals("Creator", map["Created By"])
        assertEquals("Category", map["Category"])
        assertEquals("10:00 - 12:00", map["Meetup Time"])
        assertEquals("Weekly", map["Recurring"])
        assertEquals("Monday, Wednesday, ", map["Recurring Days"])
        assertEquals("Location", map["Location"])
        assertEquals("Link", map["Link"])
        assertEquals("Description", map["Description"])
        assertEquals("01-01-2023 - 01-01-2023", map["Meetup Date"])

        unmockkObject(TimeUtils)
    }

    @Test
    fun testGetHashMapNullValues() {
        mockkObject(TimeUtils)
        every { TimeUtils.getFormattedDate(any<Long>()) } returns "01-01-2023"

        val meetup = RealmMeetup()
        val map = RealmMeetup.getHashMap(meetup)

        assertEquals("", map["Meetup Title"])
        assertEquals("", map["Created By"])
        assertEquals("", map["Category"])
        assertEquals("01-01-2023 - 01-01-2023", map["Meetup Date"])
        assertEquals(" - ", map["Meetup Time"])
        assertEquals("none", map["Recurring"])
        assertEquals("", map["Recurring Days"])
        assertEquals("", map["Location"])
        assertEquals("", map["Link"])
        assertEquals("", map["Description"])

        unmockkObject(TimeUtils)
    }

    @Test
    fun testSerialize() {
        val meetup = RealmMeetup()
        meetup.meetupId = "meetupId"
        meetup.meetupIdRev = "meetupIdRev"
        meetup.title = "Title"
        meetup.description = "Description"
        meetup.startDate = 1000L
        meetup.endDate = 2000L
        meetup.startTime = "10:00"
        meetup.endTime = "11:00"
        meetup.recurring = "None"
        meetup.meetupLocation = "Location"
        meetup.meetupLink = "Link"
        meetup.creator = "Creator"
        meetup.teamId = "TeamId"
        meetup.category = "Category"
        meetup.createdDate = 3000L
        meetup.recurringNumber = 5
        meetup.sourcePlanet = "SourcePlanet"
        meetup.sync = "Sync"
        meetup.link = "{\"teams\": \"team1\"}"

        val serialized = RealmMeetup.serialize(meetup)

        assertEquals("meetupId", serialized.get("_id").asString)
        assertEquals("meetupIdRev", serialized.get("_rev").asString)
        assertEquals("Title", serialized.get("title").asString)
        assertEquals("Description", serialized.get("description").asString)
        assertEquals(1000L, serialized.get("startDate").asLong)
        assertEquals(2000L, serialized.get("endDate").asLong)
        assertEquals("10:00", serialized.get("startTime").asString)
        assertEquals("11:00", serialized.get("endTime").asString)
        assertEquals("None", serialized.get("recurring").asString)
        assertEquals("Location", serialized.get("meetupLocation").asString)
        assertEquals("Link", serialized.get("meetupLink").asString)
        assertEquals("Creator", serialized.get("createdBy").asString)
        assertEquals("TeamId", serialized.get("teamId").asString)
        assertEquals("Category", serialized.get("category").asString)
        assertEquals(3000L, serialized.get("createdDate").asLong)
        assertEquals(5, serialized.get("recurringNumber").asInt)
        assertEquals("SourcePlanet", serialized.get("sourcePlanet").asString)
        assertEquals("Sync", serialized.get("sync").asString)
        assertEquals("team1", serialized.getAsJsonObject("link").get("teams").asString)
    }

}
