package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DocumentIdentityExtensionsTest {
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
    fun addDocumentIdentityStampsDeviceAndAppIdentity() {
        val json = JsonObject().addDocumentIdentity()

        assertEquals("uniqueIdentifier", json.get("androidId").asString)
        assertEquals("myplanet", json.get("app").asString)
    }

    @Test
    fun addDocumentIdentityUsesProvidedAndroidId() {
        val json = JsonObject().addDocumentIdentity("explicitAndroidId")

        assertEquals("explicitAndroidId", json.get("androidId").asString)
        assertEquals(APP_IDENTIFIER, json.get("app").asString)
    }
}
