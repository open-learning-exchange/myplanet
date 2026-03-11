package org.ole.planet.myplanet.data.api

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

class ApiClientTest {

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testExtractEndpointFromStackTrace_Exception() {
        mockkObject(ApiClient)

        // Mock getStackTrace to throw an exception to test the catch block
        every { ApiClient.getStackTrace() } throws RuntimeException("Mocked exception for testing")

        val method = ApiClient::class.java.getDeclaredMethod("extractEndpointFromStackTrace")
        method.isAccessible = true

        val result = method.invoke(ApiClient) as String
        assertEquals("unknown_endpoint", result)
    }
}
