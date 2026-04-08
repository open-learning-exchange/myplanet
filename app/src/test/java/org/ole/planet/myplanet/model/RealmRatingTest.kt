package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.MainApplication

class RealmRatingTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockQuery: RealmQuery<RealmRating>

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(MainApplication.Companion)
        every { MainApplication.context } returns mockContext

        every { mockContext.applicationContext } returns mockContext

        // Mock static methods
        mockkObject(NetworkUtils)
        every { NetworkUtils.getCustomDeviceName(any()) } answers { "customDeviceName" }
        every { NetworkUtils.getDeviceName() } answers { "deviceName" }
        every { NetworkUtils.getUniqueIdentifier() } answers { "uniqueIdentifier" }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSerializeRating() {
        val rating = RealmRating().apply {
            _id = "test_id"
            _rev = "test_rev"
            user = "{ \"_id\": \"test_user_id\" }"
            item = "test_item"
            type = "test_type"
            title = "test_title"
            time = 123456789L
            comment = "test_comment"
            rate = 5
            createdOn = "2023-10-26"
            parentCode = "test_parent_code"
            planetCode = "test_planet_code"
        }

        val serialized = RealmRating.serializeRating(rating)

        assertEquals("test_id", serialized.get("_id").asString)
        assertEquals("test_rev", serialized.get("_rev").asString)
        assertEquals("test_item", serialized.get("item").asString)
        assertEquals("test_type", serialized.get("type").asString)
        assertEquals("test_title", serialized.get("title").asString)
        assertEquals(123456789L, serialized.get("time").asLong)
        assertEquals("test_comment", serialized.get("comment").asString)
        assertEquals(5, serialized.get("rate").asInt)
        assertEquals("2023-10-26", serialized.get("createdOn").asString)
        assertEquals("test_parent_code", serialized.get("parentCode").asString)
        assertEquals("test_planet_code", serialized.get("planetCode").asString)
        assertEquals("customDeviceName", serialized.get("customDeviceName").asString)
        assertEquals("deviceName", serialized.get("deviceName").asString)
        assertEquals("uniqueIdentifier", serialized.get("androidId").asString)
        assertEquals("test_user_id", serialized.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun testInsertNewRating() {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "new_rating_id")
            addProperty("_rev", "new_rev")
            addProperty("time", 987654321L)
            addProperty("title", "new_title")
            addProperty("type", "new_type")
            addProperty("item", "new_item")
            addProperty("rate", 4)
            addProperty("comment", "new_comment")
            add("user", JsonObject().apply { addProperty("_id", "new_user_id") })
            addProperty("parentCode", "new_parent_code")
            addProperty("planetCode", "new_planet_code")
            addProperty("createdOn", "2023-10-27")
        }

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "new_rating_id") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockRating = mockk<RealmRating>(relaxed = true)
        every { mockRealm.createObject(RealmRating::class.java, "new_rating_id") } returns mockRating

        RealmRating.insert(mockRealm, jsonObject)

        verify { mockRealm.createObject(RealmRating::class.java, "new_rating_id") }
        verify { mockRating._rev = "new_rev" }
        verify { mockRating._id = "new_rating_id" }
        verify { mockRating.time = 987654321L }
        verify { mockRating.title = "new_title" }
        verify { mockRating.type = "new_type" }
        verify { mockRating.item = "new_item" }
        verify { mockRating.rate = 4 }
        verify { mockRating.isUpdated = false }
        verify { mockRating.comment = "new_comment" }
        verify { mockRating.user = "{\"_id\":\"new_user_id\"}" }
        verify { mockRating.userId = "new_user_id" }
        verify { mockRating.parentCode = "new_parent_code" }
        verify { mockRating.planetCode = "new_planet_code" }
        verify { mockRating.createdOn = "2023-10-27" }
    }

    @Test
    fun testInsertExistingRating() {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "existing_rating_id")
            addProperty("_rev", "existing_rev")
            addProperty("time", 111111111L)
            addProperty("title", "existing_title")
            addProperty("type", "existing_type")
            addProperty("item", "existing_item")
            addProperty("rate", 3)
            addProperty("comment", "existing_comment")
            add("user", JsonObject().apply { addProperty("_id", "existing_user_id") })
            addProperty("parentCode", "existing_parent_code")
            addProperty("planetCode", "existing_planet_code")
            addProperty("createdOn", "2023-10-28")
        }

        val mockRating = mockk<RealmRating>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "existing_rating_id") } returns mockQuery
        every { mockQuery.findFirst() } returns mockRating

        RealmRating.insert(mockRealm, jsonObject)

        verify(exactly = 0) { mockRealm.createObject(RealmRating::class.java, "existing_rating_id") }
        verify { mockRating._rev = "existing_rev" }
        verify { mockRating._id = "existing_rating_id" }
        verify { mockRating.time = 111111111L }
        verify { mockRating.title = "existing_title" }
        verify { mockRating.type = "existing_type" }
        verify { mockRating.item = "existing_item" }
        verify { mockRating.rate = 3 }
        verify { mockRating.isUpdated = false }
        verify { mockRating.comment = "existing_comment" }
        verify { mockRating.user = "{\"_id\":\"existing_user_id\"}" }
        verify { mockRating.userId = "existing_user_id" }
        verify { mockRating.parentCode = "existing_parent_code" }
        verify { mockRating.planetCode = "existing_planet_code" }
        verify { mockRating.createdOn = "2023-10-28" }
    }
}
