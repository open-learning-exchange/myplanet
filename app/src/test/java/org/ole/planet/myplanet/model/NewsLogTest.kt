package org.ole.planet.myplanet.model

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.NetworkUtils

class NewsLogTest {
    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getDeviceName() } returns "mock_device"
        every { NetworkUtils.getUniqueIdentifier() } returns "mock_android_id"
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `serialize builds every field via the kotlinx json builder`() {
        val log = NewsLog().apply {
            userId = "user1"
            type = "news"
            time = 100L
        }

        val result = NewsLog.serialize(log, "custom-device")

        assertEquals("user1", result.get("user").asString)
        assertEquals("news", result.get("type").asString)
        assertEquals(100L, result.get("time").asLong)
        assertEquals("mock_device", result.get("deviceName").asString)
        assertEquals("custom-device", result.get("customDeviceName").asString)
        assertEquals("mock_android_id", result.get("androidId").asString)
        assertEquals("myplanet", result.get("app").asString)
    }

    @Test
    fun `serialize represents a null field as json null, matching prior Gson addProperty behavior`() {
        val log = NewsLog().apply {
            userId = "user1"
            type = null
            time = null
        }

        val result = NewsLog.serialize(log, "custom-device")

        assertTrue(result.get("type").isJsonNull)
        assertTrue(result.get("time").isJsonNull)
    }
}
