package org.ole.planet.myplanet.data.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.SyncTimeLogger
import retrofit2.Response

class ApiClientTest {

    @Before
    fun setup() {
        mockkObject(SyncTimeLogger)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `executeWithRetryAndWrap returns result even when logging throws exception`() = runTest {
        // Arrange
        val expectedResponse = Response.success("Test Data")

        // Force logApiCall to throw an exception to test the catch block
        every {
            SyncTimeLogger.logApiCall(
                any(),
                any(),
                any(),
                any()
            )
        } throws RuntimeException("Forced logging error")

        // Act
        val actualResponse = ApiClient.executeWithRetryAndWrap {
            expectedResponse
        }

        // Assert
        // The method should catch the exception and return the expected response normally
        assertNotNull(actualResponse)
        assertTrue(actualResponse!!.isSuccessful)
        assertEquals("Test Data", actualResponse.body())

        // Verify the logger was actually called and threw the exception
        verify(exactly = 1) {
            SyncTimeLogger.logApiCall(
                any(),
                any(),
                any(),
                any()
            )
        }
    }
}
