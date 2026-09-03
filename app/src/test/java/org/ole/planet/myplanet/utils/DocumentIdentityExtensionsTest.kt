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
    fun addAppIdentityStampsDeviceAndAppIdentity() {
        val json = JsonObject().addAppIdentity()

        assertEquals("uniqueIdentifier", json.get("androidId").asString)
        assertEquals("myplanet", json.get("app").asString)
    }

    @Test
    fun addAppIdentityUsesProvidedAndroidId() {
        val json = JsonObject().addAppIdentity("explicitAndroidId")

        assertEquals("explicitAndroidId", json.get("androidId").asString)
        assertEquals(APP_IDENTIFIER, json.get("app").asString)
    }
}
