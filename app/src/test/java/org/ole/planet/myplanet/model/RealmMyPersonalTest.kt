package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class RealmMyPersonalTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk<Context>(relaxed = true)

        mockkStatic(FileUtils::class)
        // Using mockkObject to mock the singleton methods completely instead of static
        mockkObject(NetworkUtils)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSerialize() {
        val personal = RealmMyPersonal().apply {
            title = "My Personal Title"
            date = 1600000000000L
            path = "http://example.com/file.pdf"
            userName = "John Doe"
            description = "This is a description"
            userId = "user123"
        }

        every { FileUtils.getFileNameFromUrl(any()) } returns "file.pdf"

        every { NetworkUtils.getUniqueIdentifier() } returns "unique_id"
        every { NetworkUtils.getDeviceName() } returns "device_name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom_device_name"

        val serialized = RealmMyPersonal.serialize(personal, mockContext)

        assertEquals("My Personal Title", serialized.get("title").asString)
        assertTrue(serialized.has("uploadDate"))
        assertEquals(1600000000000L, serialized.get("createdDate").asLong)
        assertEquals("file.pdf", serialized.get("filename").asString)
        assertEquals("John Doe", serialized.get("author").asString)
        assertEquals("John Doe", serialized.get("addedBy").asString)
        assertEquals("This is a description", serialized.get("description").asString)
        assertEquals("Activities", serialized.get("resourceType").asString)
        assertTrue(serialized.get("private").asBoolean)

        assertEquals("unique_id", serialized.get("androidId").asString)
        assertEquals("device_name", serialized.get("deviceName").asString)
        assertEquals("custom_device_name", serialized.get("customDeviceName").asString)

        val privateFor = serialized.getAsJsonObject("privateFor")
        assertEquals("user123", privateFor.get("users").asString)
    }

    @Test
    fun testSerialize_withNullValues() {
        val personal = RealmMyPersonal() // all nullable fields are null by default

        every { FileUtils.getFileNameFromUrl(null) } returns ""
        every { NetworkUtils.getUniqueIdentifier() } returns "unique_id"
        every { NetworkUtils.getDeviceName() } returns "device_name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom_device_name"

        val serialized = RealmMyPersonal.serialize(personal, mockContext)

        assertTrue(serialized.get("title").isJsonNull)
        assertTrue(serialized.has("uploadDate"))
        assertEquals(0L, serialized.get("createdDate").asLong)
        assertEquals("", serialized.get("filename").asString)
        assertTrue(serialized.get("author").isJsonNull)
        assertTrue(serialized.get("addedBy").isJsonNull)
        assertTrue(serialized.get("description").isJsonNull)
        assertEquals("Activities", serialized.get("resourceType").asString)
        assertTrue(serialized.get("private").asBoolean)

        assertEquals("unique_id", serialized.get("androidId").asString)
        assertEquals("device_name", serialized.get("deviceName").asString)
        assertEquals("custom_device_name", serialized.get("customDeviceName").asString)

        val privateFor = serialized.getAsJsonObject("privateFor")
        assertTrue(privateFor.get("users").isJsonNull)
    }
}
