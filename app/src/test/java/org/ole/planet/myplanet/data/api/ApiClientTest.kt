package org.ole.planet.myplanet.data.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ApiClientTest {

    @Test
    fun `executeWithResult returns Success when response is successful and body is not null`() = runTest {
        val mockResponse = mockk<Response<String>>()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns "SuccessBody"

        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Success)
        assertEquals("SuccessBody", (result as NetworkResult.Success).data)
    }

    @Test
    fun `executeWithResult returns Error when response is successful but body is null`() = runTest {
        val mockResponse = mockk<Response<String>>()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns null
        every { mockResponse.code() } returns 204

        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Error)
        assertEquals(204, (result as NetworkResult.Error).code)
        assertEquals(null, (result as NetworkResult.Error).message)
    }

    @Test
    fun `executeWithResult retries and then returns Error with errorBody on failure`() = runTest {
        val mockResponse = mockk<Response<String>>()
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code() } returns 400
        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } returns "Error Content"
        every { mockResponse.errorBody() } returns mockResponseBody

        var callCount = 0
        val result = ApiClient.executeWithResult {
            callCount++
            mockResponse
        }

        assertEquals(3, callCount)
        assertTrue(result is NetworkResult.Error)
        assertEquals(400, (result as NetworkResult.Error).code)
        assertEquals("Error Content", result.message)
    }

    @Test
    fun `executeWithResult returns Error with null message when errorBody throws exception`() = runTest {
        val mockResponse = mockk<Response<String>>()
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code() } returns 500
        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } throws RuntimeException("Error body read failed")
        every { mockResponse.errorBody() } returns mockResponseBody

        var callCount = 0
        val result = ApiClient.executeWithResult {
            callCount++
            mockResponse
        }

        assertEquals(3, callCount)
        assertTrue(result is NetworkResult.Error)
        assertEquals(500, (result as NetworkResult.Error).code)
        assertEquals(null, result.message)
    }

    @Test
    fun `executeWithResult returns Exception when operation throws SocketTimeoutException`() = runTest {
        val exception = SocketTimeoutException("Timeout")

        var callCount = 0
        val result = ApiClient.executeWithResult<String> {
            callCount++
            throw exception
        }

        assertEquals(3, callCount)
        assertTrue(result is NetworkResult.Exception)
        assertEquals(exception, (result as NetworkResult.Exception).exception)
    }

    @Test
    fun `executeWithResult returns Exception when operation throws IOException`() = runTest {
        val exception = IOException("IO Error")

        var callCount = 0
        val result = ApiClient.executeWithResult<String> {
            callCount++
            throw exception
        }

        assertEquals(3, callCount)
        assertTrue(result is NetworkResult.Exception)
        assertEquals(exception, (result as NetworkResult.Exception).exception)
    }

    @Test
    fun `executeWithResult returns Exception when operation throws generic Exception`() = runTest {
        val exception = Exception("Generic Error")

        var callCount = 0
        val result = ApiClient.executeWithResult<String> {
            callCount++
            throw exception
        }

        assertEquals(3, callCount)
        assertTrue(result is NetworkResult.Exception)
        assertEquals(exception, (result as NetworkResult.Exception).exception)
    }
}
