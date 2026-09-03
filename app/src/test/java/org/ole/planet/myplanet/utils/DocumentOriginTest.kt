package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DocumentOriginTest {
    @Before
    fun setUp() {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "uniqueIdentifier"
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
    }

    @Test
    fun addDocumentOriginStampsDeviceAndApp() {
        val json = JsonObject().addDocumentOrigin()

        assertEquals("uniqueIdentifier", json.get("androidId").asString)
        assertEquals("myplanet", json.get("app").asString)
    }

    @Test
    fun addDocumentOriginUsesProvidedAndroidId() {
        val json = JsonObject().addDocumentOrigin("explicitAndroidId")

        assertEquals("explicitAndroidId", json.get("androidId").asString)
        assertEquals(DOCUMENT_APP_IDENTIFIER, json.get("app").asString)
    }
}
