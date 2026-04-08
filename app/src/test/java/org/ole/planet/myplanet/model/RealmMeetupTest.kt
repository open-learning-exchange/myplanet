package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import android.text.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.TimeUtils
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After

class RealmMeetupTest {

    @Before
    fun setup() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = firstArg<CharSequence?>()
            str == null || str.length == 0
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInsertNewMeetup() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockMeetup = mockk<RealmMeetup>(relaxed = true)

        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "test_id") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmMeetup::class.java, "test_id") } returns mockMeetup

        val doc = JsonObject()
        doc.addProperty("_id", "test_id")
        doc.addProperty("_rev", "test_rev")
        doc.addProperty("title", "Test Title")

        RealmMeetup.insert("user123", doc, mockRealm)

        verify(exactly = 1) { mockMeetup.meetupId = "test_id" }
        verify(exactly = 1) { mockMeetup.userId = "user123" }
        verify(exactly = 1) { mockMeetup.meetupIdRev = "test_rev" }
        verify(exactly = 1) { mockMeetup.title = "Test Title" }
    }

    @Test
    fun testInsertExistingMeetup() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockMeetup = mockk<RealmMeetup>(relaxed = true)

        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.equalTo("meetupId", "test_id") } returns mockQuery
        every { mockQuery.findFirst() } returns mockMeetup

        val doc = JsonObject()
        doc.addProperty("_id", "test_id")
        doc.addProperty("_rev", "test_rev")
        doc.addProperty("title", "Updated Title")

        RealmMeetup.insert("user123", doc, mockRealm)

        verify(exactly = 0) { mockRealm.createObject(RealmMeetup::class.java, any<String>()) }
        verify(exactly = 1) { mockMeetup.meetupId = "test_id" }
        verify(exactly = 1) { mockMeetup.title = "Updated Title" }
    }

    @Test
    fun testGetMyMeetUpIds() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMeetup>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMeetup>>(relaxed = true)

        every { mockRealm.where(RealmMeetup::class.java) } returns mockQuery
        every { mockQuery.isNotEmpty("userId") } returns mockQuery
        every { mockQuery.equalTo("userId", "user123", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val meetup1 = RealmMeetup().apply { meetupId = "id1" }
        val meetup2 = RealmMeetup().apply { meetupId = "id2" }

        every { mockResults.iterator() } returns mutableListOf(meetup1, meetup2).iterator()

        val ids = RealmMeetup.getMyMeetUpIds(mockRealm, "user123")

        assertEquals(2, ids.size())
        assertEquals("id1", ids.get(0).asString)
        assertEquals("id2", ids.get(1).asString)
    }

    @Test
    fun testGetHashMap() {
        io.mockk.mockkConstructor(org.json.JSONArray::class)
        every { anyConstructed<org.json.JSONArray>().length() } returns 2
        every { anyConstructed<org.json.JSONArray>().get(0) } returns "Monday"
        every { anyConstructed<org.json.JSONArray>().get(1) } returns "Wednesday"

        val meetup = RealmMeetup().apply {
            title = "Sample Meetup"
            creator = "John Doe"
            category = "Tech"
            startDate = 1609459200000L // Jan 1, 2021
            endDate = 1609545600000L   // Jan 2, 2021
            startTime = "10:00 AM"
            endTime = "12:00 PM"
            recurring = "weekly"
            day = "[\"Monday\", \"Wednesday\"]"
            meetupLocation = "Room 101"
            meetupLink = "http://example.com"
            description = "A great meetup"
        }

        val map = RealmMeetup.getHashMap(meetup)

        assertEquals("Sample Meetup", map["Meetup Title"])
        assertEquals("John Doe", map["Created By"])
        assertEquals("Tech", map["Category"])
        assertEquals("10:00 AM - 12:00 PM", map["Meetup Time"])
        assertEquals("weekly", map["Recurring"])
        assertEquals("Monday, Wednesday", map["Recurring Days"])
        assertEquals("Room 101", map["Location"])
        assertEquals("http://example.com", map["Link"])
        assertEquals("A great meetup", map["Description"])

        // Use TimeUtils to generate expected string to avoid time zone issues
        val expectedDateStr = TimeUtils.getFormattedDate(1609459200000L) + " - " + TimeUtils.getFormattedDate(1609545600000L)
        assertEquals(expectedDateStr, map["Meetup Date"])
    }

    @Test
    fun testSerializeWithMalformedDay() {
        val meetup = RealmMeetup().apply {
            meetupId = "m1"
            day = "malformed_json"
        }

        val originalSystemErr = System.err
        System.setErr(java.io.PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) {}
        }))

        // This should not throw an exception
        val json = RealmMeetup.serialize(meetup)

        System.setErr(originalSystemErr)

        // Assert basic fields are still serialized
        assertEquals("m1", json.get("_id").asString)
        // Assert day is not present due to the exception
        assertEquals(false, json.has("day"))
    }

    @Test
    fun testSerialize() {
        val meetup = RealmMeetup().apply {
            meetupId = "m1"
            meetupIdRev = "rev1"
            title = "Test Title"
            description = "Test Desc"
            startDate = 1000L
            endDate = 2000L
            startTime = "10:00"
            endTime = "11:00"
            recurring = "none"
            day = "[\"Monday\"]"
            meetupLocation = "Loc"
            meetupLink = "Link"
            creator = "Creator"
            teamId = "t1"
            category = "Cat"
            createdDate = 500L
            recurringNumber = 5
            sourcePlanet = "Earth"
            sync = "true"
            link = "{\"key\":\"value\"}"
        }

        val json = RealmMeetup.serialize(meetup)

        assertEquals("m1", json.get("_id").asString)
        assertEquals("rev1", json.get("_rev").asString)
        assertEquals("Test Title", json.get("title").asString)
        assertEquals("Test Desc", json.get("description").asString)
        assertEquals(1000L, json.get("startDate").asLong)
        assertEquals("10:00", json.get("startTime").asString)
        assertEquals("Monday", json.getAsJsonArray("day").get(0).asString)
        assertEquals("Earth", json.get("sourcePlanet").asString)
        assertEquals("value", json.getAsJsonObject("link").get("key").asString)
    }
}
