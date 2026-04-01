package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.MainApplication
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], application = android.app.Application::class)
class RealmRatingTest {

    private var originalContext: Context? = null

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        try {
            originalContext = MainApplication.context
        } catch (e: Exception) {
            originalContext = null
        }
        val mockContext = mockk<Context>(relaxed = true)
        MainApplication.context = mockContext

        every { NetworkUtils.getCustomDeviceName(any()) } returns "CustomDevice"
        every { NetworkUtils.getDeviceName() } returns "DeviceName"
        every { NetworkUtils.getUniqueIdentifier() } returns "AndroidId"
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
        if (originalContext != null) {
            MainApplication.context = originalContext!!
        }
    }

    @Test
    fun testSerializeRating() {
        val rating = mockk<RealmRating>(relaxed = true)
        every { rating._id } returns "test_id"
        every { rating._rev } returns "test_rev"
        every { rating.user } returns "{\"_id\":\"user_id\"}"
        every { rating.item } returns "test_item"
        every { rating.type } returns "test_type"
        every { rating.title } returns "test_title"
        every { rating.time } returns 12345L
        every { rating.comment } returns "test_comment"
        every { rating.rate } returns 5
        every { rating.createdOn } returns "2023-01-01"
        every { rating.parentCode } returns "parent_code"
        every { rating.planetCode } returns "planet_code"

        val jsonObject = RealmRating.serializeRating(rating)

        assertEquals("test_id", jsonObject.get("_id").asString)
        assertEquals("test_rev", jsonObject.get("_rev").asString)

        val userStr = jsonObject.get("user").toString()
        assert(userStr.contains("user_id"))

        assertEquals("test_item", jsonObject.get("item").asString)
        assertEquals("test_type", jsonObject.get("type").asString)
        assertEquals("test_title", jsonObject.get("title").asString)
        assertEquals(12345L, jsonObject.get("time").asLong)
        assertEquals("test_comment", jsonObject.get("comment").asString)
        assertEquals(5, jsonObject.get("rate").asInt)
        assertEquals("2023-01-01", jsonObject.get("createdOn").asString)
        assertEquals("parent_code", jsonObject.get("parentCode").asString)
        assertEquals("planet_code", jsonObject.get("planetCode").asString)
        assertEquals("CustomDevice", jsonObject.get("customDeviceName").asString)
        assertEquals("DeviceName", jsonObject.get("deviceName").asString)
        assertEquals("AndroidId", jsonObject.get("androidId").asString)
    }

    @Test
    fun testInsert_newRating() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmRating>>()

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", any<String>()) } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val newRating = mockk<RealmRating>(relaxed = true)
        every { mockRealm.createObject(RealmRating::class.java, "test_id") } returns newRating

        val act = JsonObject()
        act.addProperty("_id", "test_id")
        act.addProperty("_rev", "test_rev")
        act.addProperty("time", 12345L)
        act.addProperty("title", "test_title")
        act.addProperty("type", "test_type")
        act.addProperty("item", "test_item")
        act.addProperty("rate", 5)
        act.addProperty("comment", "test_comment")
        val userObj = JsonObject()
        userObj.addProperty("_id", "user_id")
        act.add("user", userObj)
        act.addProperty("parentCode", "parent_code")
        act.addProperty("planetCode", "planet_code")
        act.addProperty("createdOn", "2023-01-01")

        RealmRating.insert(mockRealm, act)

        verify(exactly = 1) { newRating._rev = "test_rev" }
        verify(exactly = 1) { newRating._id = "test_id" }
        verify(exactly = 1) { newRating.time = 12345L }
        verify(exactly = 1) { newRating.title = "test_title" }
        verify(exactly = 1) { newRating.type = "test_type" }
        verify(exactly = 1) { newRating.item = "test_item" }
        verify(exactly = 1) { newRating.rate = 5 }
        verify(exactly = 1) { newRating.isUpdated = false }
        verify(exactly = 1) { newRating.comment = "test_comment" }
        verify(exactly = 1) { newRating.user = "{\"_id\":\"user_id\"}" }
        verify(exactly = 1) { newRating.userId = "user_id" }
        verify(exactly = 1) { newRating.parentCode = "planet_code" }
        verify(exactly = 1) { newRating.createdOn = "2023-01-01" }
    }

    @Test
    fun testInsert_existingRating() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmRating>>()
        val existingRating = mockk<RealmRating>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", any<String>()) } returns mockQuery
        every { mockQuery.findFirst() } returns existingRating

        val act = JsonObject()
        act.addProperty("_id", "test_id")
        act.addProperty("_rev", "test_rev")
        act.addProperty("time", 12345L)
        act.addProperty("title", "test_title")
        act.addProperty("type", "test_type")
        act.addProperty("item", "test_item")
        act.addProperty("rate", 5)
        act.addProperty("comment", "test_comment")
        val userObj = JsonObject()
        userObj.addProperty("_id", "user_id")
        act.add("user", userObj)
        act.addProperty("parentCode", "parent_code")
        act.addProperty("planetCode", "planet_code")
        act.addProperty("createdOn", "2023-01-01")

        RealmRating.insert(mockRealm, act)

        verify(exactly = 0) { mockRealm.createObject(RealmRating::class.java, "test_id") }

        verify(exactly = 1) { existingRating._rev = "test_rev" }
        verify(exactly = 1) { existingRating._id = "test_id" }
        verify(exactly = 1) { existingRating.time = 12345L }
        verify(exactly = 1) { existingRating.title = "test_title" }
        verify(exactly = 1) { existingRating.type = "test_type" }
        verify(exactly = 1) { existingRating.item = "test_item" }
        verify(exactly = 1) { existingRating.rate = 5 }
        verify(exactly = 1) { existingRating.isUpdated = false }
        verify(exactly = 1) { existingRating.comment = "test_comment" }
        verify(exactly = 1) { existingRating.user = "{\"_id\":\"user_id\"}" }
        verify(exactly = 1) { existingRating.userId = "user_id" }
        verify(exactly = 1) { existingRating.parentCode = "planet_code" }
        verify(exactly = 1) { existingRating.createdOn = "2023-01-01" }
    }
}
