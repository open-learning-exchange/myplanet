package org.ole.planet.myplanet.model

import android.app.Application
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
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
class MeetupTest {

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fromJson with new meetup creates and sets properties`() {
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

        val meetup = Meetup.fromJson(meetupDoc, userId, null)

        assertEquals("meetup1", meetup.id)
        assertEquals("meetup1", meetup.meetupId)
        assertEquals(userId, meetup.userId)
        assertEquals("rev1", meetup.meetupIdRev)
        assertEquals("Test Meetup", meetup.title)
        assertEquals("Test Description", meetup.description)
        assertEquals(1600000000000, meetup.startDate)
        assertEquals(1600003600000, meetup.endDate)
        assertEquals("weekly", meetup.recurring)
        assertEquals("10:00", meetup.startTime)
        assertEquals("11:00", meetup.endTime)
        assertEquals("tech", meetup.category)
        assertEquals("Room 1", meetup.meetupLocation)
        assertEquals("http://meetup.com", meetup.meetupLink)
        assertEquals("creator1", meetup.creator)
        assertEquals("""["Monday"]""", meetup.day)
        assertEquals("""{"teams":"team1"}""", meetup.link)
        assertEquals("team1", meetup.teamId)
    }

    @Test
    fun `fromJson with existing meetup preserves local fields`() {
        val userId = "user123"
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
            addProperty("_rev", "rev2")
            addProperty("title", "Updated Meetup")
        }

        val existingMeetup = Meetup()
        existingMeetup.createdDate = 12345L
        existingMeetup.sync = "synced"

        val meetup = Meetup.fromJson(meetupDoc, userId, existingMeetup)

        assertEquals("meetup1", meetup.meetupId)
        assertEquals(userId, meetup.userId)
        assertEquals("rev2", meetup.meetupIdRev)
        assertEquals("Updated Meetup", meetup.title)
        assertEquals(12345L, meetup.createdDate)
        assertEquals("synced", meetup.sync)
    }

    @Test
    fun `fromJson without userId uses empty string`() {
        val meetupDoc = JsonObject().apply {
            addProperty("_id", "meetup1")
        }

        val meetup = Meetup.fromJson(meetupDoc, "", null)

        assertEquals("", meetup.userId)
    }

    @Test
    fun `getMyMeetUpIds returns json array of ids`() {
        val meetup1 = Meetup().apply { meetupId = "id1" }
        val meetup2 = Meetup().apply { meetupId = "id2" }

        val result = Meetup.getMyMeetUpIds(listOf(meetup1, meetup2))

        assertEquals(2, result.size())
        assertEquals("id1", result[0].asString)
        assertEquals("id2", result[1].asString)
    }

    @Test
    fun `getMyMeetUpIds with empty list returns empty array`() {
        val result = Meetup.getMyMeetUpIds(emptyList())
        assertEquals(0, result.size())
    }

    @Test
    fun `getHashMap extracts correct map values`() {
        val meetup = mockk<Meetup>()
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

        val map = Meetup.getHashMap(meetup)

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
        val meetup = mockk<Meetup>(relaxed = true)
        every { meetup.title } returns null
        every { meetup.creator } returns null
        every { meetup.day } returns "[]" // valid empty array to avoid JSONException console pollution

        val map = Meetup.getHashMap(meetup)

        assertEquals("", map["Meetup Title"])
        assertEquals("", map["Created By"])
        assertEquals("", map["Recurring Days"])
    }

    @Test
    fun `serialize creates correct JsonObject`() {
        val meetup = Meetup().apply {
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

        val jsonObject = Meetup.serialize(meetup)

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
        val meetup = Meetup().apply {
            meetupId = ""
            meetupIdRev = null
            link = null
        }

        val jsonObject = Meetup.serialize(meetup)

        assertEquals(false, jsonObject.has("_id"))
        assertEquals(false, jsonObject.has("_rev"))
        assertEquals(false, jsonObject.has("link"))
    }

    @Test
    fun `getAllEventDates and occursOnDate work correctly for date range`() {
        val startCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 20, 10, 0, 0)
        }
        val endCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 23, 11, 0, 0)
        }

        val meetup = Meetup().apply {
            startDate = startCal.timeInMillis
            endDate = endCal.timeInMillis
            recurring = "none"
        }

        val dates = meetup.getAllEventDates()
        assertEquals(4, dates.size)
        assertEquals(20, dates[0].get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(21, dates[1].get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(22, dates[2].get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(23, dates[3].get(java.util.Calendar.DAY_OF_MONTH))

        val july22 = java.time.LocalDate.of(2026, 7, 22)
        val july24 = java.time.LocalDate.of(2026, 7, 24)

        assertEquals(true, meetup.occursOnDate(july22))
        assertEquals(false, meetup.occursOnDate(july24))
    }

    @Test
    fun `getAllEventDates and occursOnDate work correctly for daily recurring`() {
        val startCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 20, 10, 0, 0)
        }

        val meetup = Meetup().apply {
            startDate = startCal.timeInMillis
            endDate = startCal.timeInMillis
            recurring = "daily"
            recurringNumber = 5
        }

        val dates = meetup.getAllEventDates()
        assertEquals(5, dates.size)

        val july22 = java.time.LocalDate.of(2026, 7, 22)
        val july26 = java.time.LocalDate.of(2026, 7, 26)

        assertEquals(true, meetup.occursOnDate(july22))
        assertEquals(false, meetup.occursOnDate(july26))
    }

    @Test
    fun `getAllEventDates and occursOnDate work correctly for weekly recurring`() {
        val startCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 20, 10, 0, 0) // Monday
        }

        val meetup = Meetup().apply {
            startDate = startCal.timeInMillis
            endDate = startCal.timeInMillis
            recurring = "weekly"
            recurringNumber = 4
        }

        val dates = meetup.getAllEventDates()
        assertEquals(4, dates.size)
        assertEquals(20, dates[0].get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(27, dates[1].get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(3, dates[2].get(java.util.Calendar.DAY_OF_MONTH)) // August 3

        val july27 = java.time.LocalDate.of(2026, 7, 27)
        val july21 = java.time.LocalDate.of(2026, 7, 21)

        assertEquals(true, meetup.occursOnDate(july27))
        assertEquals(false, meetup.occursOnDate(july21))
    }

    @Test
    fun `getAllEventDates and occursOnDate work correctly for recurring events with explicit end date`() {
        val startCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 20, 10, 0, 0)
        }
        val endCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 25, 10, 0, 0)
        }

        val meetup = Meetup().apply {
            startDate = startCal.timeInMillis
            endDate = endCal.timeInMillis
            recurring = "daily"
        }

        val dates = meetup.getAllEventDates()
        assertEquals(6, dates.size)
        assertEquals(20, dates.first().get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(25, dates.last().get(java.util.Calendar.DAY_OF_MONTH))

        val july23 = java.time.LocalDate.of(2026, 7, 23)
        val july26 = java.time.LocalDate.of(2026, 7, 26)

        assertEquals(true, meetup.occursOnDate(july23))
        assertEquals(false, meetup.occursOnDate(july26))
    }
}
