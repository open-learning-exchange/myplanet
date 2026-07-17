package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.NetworkUtils

class RealmRatingTest {

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(MainApplication.Companion)
        every { MainApplication.context } returns mockContext
        every { mockContext.applicationContext } returns mockContext

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
}
