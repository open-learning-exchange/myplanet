package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val json = buildJsonObject { addDocumentOrigin() }

        assertEquals("uniqueIdentifier", json.getValue("androidId").jsonPrimitive.content)
        assertEquals("myplanet", json.getValue("app").jsonPrimitive.content)
    }

    @Test
    fun addDocumentOriginUsesProvidedAndroidId() {
        val json = buildJsonObject { addDocumentOrigin("explicitAndroidId") }

        assertEquals("explicitAndroidId", json.getValue("androidId").jsonPrimitive.content)
        assertEquals(DOCUMENT_ORIGIN, json.getValue("app").jsonPrimitive.content)
    }
}
