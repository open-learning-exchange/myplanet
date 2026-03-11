package org.ole.planet.myplanet.data.api

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import okhttp3.ResponseBody.Companion.toResponseBody

class ApiClientTest {

    @Test
    fun `executeWithResult success returns Success`() = runTest {
        val mockResponse = mockk<Response<String>>()
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body() } returns "Success data"

        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Success)
        assertEquals("Success data", (result as NetworkResult.Success).data)
    }

    @Test
    fun `executeWithResult success returns Error when body is null`() = runTest {
        val mockResponse = mockk<Response<String>>()
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body() } returns null
        coEvery { mockResponse.code() } returns 200

        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Error)
        assertEquals(200, (result as NetworkResult.Error).code)
        assertEquals(null, result.message)
    }

    @Test
    fun `executeWithResult http error returns Error`() = runTest {
        val mockResponse = mockk<Response<String>>()
        coEvery { mockResponse.isSuccessful } returns false
        coEvery { mockResponse.code() } returns 404
        coEvery { mockResponse.errorBody() } returns "Not found".toResponseBody(null)

        // It retries on HTTP errors in executeWithResult if retryCount < 2, so it will take 3 tries total
        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Error)
        assertEquals(404, (result as NetworkResult.Error).code)
        assertEquals("Not found", result.message)
    }

    @Test
    fun `executeWithResult io exception returns Exception`() = runTest {
        val exception = IOException("Network error")
        val result = ApiClient.executeWithResult<String> { throw exception }

        assertTrue(result is NetworkResult.Exception)
        assertEquals(exception, (result as NetworkResult.Exception).exception)
    }

    @Test
    fun `executeWithResult socket timeout exception returns Exception`() = runTest {
        val exception = SocketTimeoutException("Timeout")
        val result = ApiClient.executeWithResult<String> { throw exception }

        assertTrue(result is NetworkResult.Exception)
        assertEquals(exception, (result as NetworkResult.Exception).exception)
    }

    @Test
    fun `executeWithResult general exception returns Exception`() = runTest {
        val exception = Exception("General error")
        val result = ApiClient.executeWithResult<String> { throw exception }

        assertTrue(result is NetworkResult.Exception)
        assertEquals(exception, (result as NetworkResult.Exception).exception)
    }

    @Test
    fun `executeWithResult null response returns Exception after retries`() = runTest {
        val result = ApiClient.executeWithResult<String> { null }

        assertTrue(result is NetworkResult.Exception)
        assertTrue((result as NetworkResult.Exception).exception.message?.contains("Unknown error") == true)
    }

    @Test
    fun `executeWithResult succeeds after retries on http error`() = runTest {
        val mockResponseSuccess = mockk<Response<String>>()
        coEvery { mockResponseSuccess.isSuccessful } returns true
        coEvery { mockResponseSuccess.body() } returns "Success data"

        val mockResponseFail = mockk<Response<String>>()
        coEvery { mockResponseFail.isSuccessful } returns false

        var attempts = 0
        val result = ApiClient.executeWithResult {
            attempts++
            if (attempts < 3) mockResponseFail else mockResponseSuccess
        }

        assertEquals(3, attempts)
        assertTrue(result is NetworkResult.Success)
        assertEquals("Success data", (result as NetworkResult.Success).data)
    }

    @Test
    fun `executeWithRetryAndWrap success on first try`() = runTest {
        val mockResponse = mockk<Response<String>>()
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body() } returns "Success data"

        var attempts = 0
        val result = ApiClient.executeWithRetryAndWrap {
            attempts++
            mockResponse
        }

        assertEquals(1, attempts)
        assertEquals(mockResponse, result)
    }

    @Test
    fun `executeWithRetryAndWrap success after retries`() = runTest {
        val mockResponseSuccess = mockk<Response<String>>()
        coEvery { mockResponseSuccess.isSuccessful } returns true
        coEvery { mockResponseSuccess.body() } returns "Success data"

        val mockResponseFail = mockk<Response<String>>()
        coEvery { mockResponseFail.isSuccessful } returns false

        var attempts = 0
        val result = ApiClient.executeWithRetryAndWrap {
            attempts++
            if (attempts < 3) mockResponseFail else mockResponseSuccess
        }

        assertEquals(3, attempts)
        assertEquals(mockResponseSuccess, result)
    }

    @Test
    fun `executeWithRetryAndWrap extracts list size for logging`() = runTest {
        val mockResponse = mockk<Response<List<String>>>()
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body() } returns listOf("A", "B", "C")

        val result = ApiClient.executeWithRetryAndWrap { mockResponse }
        assertEquals(mockResponse, result)
    }

    @Test
    fun `executeWithRetryAndWrap extracts json object rows for logging`() = runTest {
        val mockResponse = mockk<Response<JsonObject>>()
        coEvery { mockResponse.isSuccessful } returns true
        val jsonObj = JsonObject()
        val jsonArr = JsonArray()
        jsonArr.add("A")
        jsonArr.add("B")
        jsonObj.add("rows", jsonArr)
        coEvery { mockResponse.body() } returns jsonObj

        val result = ApiClient.executeWithRetryAndWrap { mockResponse }
        assertEquals(mockResponse, result)
    }

    @Test
    fun `executeWithRetryAndWrap extracts json object without rows for logging`() = runTest {
        val mockResponse = mockk<Response<JsonObject>>()
        coEvery { mockResponse.isSuccessful } returns true
        val jsonObj = JsonObject()
        coEvery { mockResponse.body() } returns jsonObj

        val result = ApiClient.executeWithRetryAndWrap { mockResponse }
        assertEquals(mockResponse, result)
    }

    @Test
    fun `executeWithRetryAndWrap handles null body for logging`() = runTest {
        val mockResponse = mockk<Response<String>>()
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body() } returns null

        val result = ApiClient.executeWithRetryAndWrap { mockResponse }
        assertEquals(mockResponse, result)
    }

    @Test
    fun `executeWithRetryAndWrap handles exception during operation`() = runTest {
        var attempts = 0
        val result = ApiClient.executeWithRetryAndWrap<String> {
            attempts++
            throw Exception("Test exception")
        }

        assertEquals(3, attempts)
        assertEquals(null, result)
    }
}
