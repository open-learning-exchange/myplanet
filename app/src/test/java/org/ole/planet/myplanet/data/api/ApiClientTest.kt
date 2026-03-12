package org.ole.planet.myplanet.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.utils.SyncTimeLogger
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

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
        coEvery { mockResponse.code() } returns 204

        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Error)
        assertEquals(204, (result as NetworkResult.Error).code)
        assertNull(result.message)
    }

    @Test
    fun `executeWithResult returns Error with body when response is unsuccessful and has errorBody`() = runTest {
        val errorJson = "{\"error\": \"Not found\"}"
        val errorResponseBody = errorJson.toResponseBody("application/json".toMediaTypeOrNull())
        val mockResponse = Response.error<Any>(404, errorResponseBody)

        val operation: suspend () -> Response<Any>? = { mockResponse }

        val result = ApiClient.executeWithResult(operation)

        assertTrue(result is NetworkResult.Error)
        assertEquals(404, (result as NetworkResult.Error).code)
        assertEquals(errorJson, result.message)
    }

    @Test
    fun `executeWithResult returns Error with null message when errorBody string throws exception`() = runTest {
        val errorResponseBody = mockk<ResponseBody>()
        every { errorResponseBody.contentType() } returns "application/json".toMediaTypeOrNull()
        every { errorResponseBody.contentLength() } returns 100L
        every { errorResponseBody.string() } throws RuntimeException("Failed to read error body")

        val mockResponse = Response.error<Any>(500, errorResponseBody)

        val operation: suspend () -> Response<Any>? = { mockResponse }

        val result = ApiClient.executeWithResult(operation)

        assertTrue(result is NetworkResult.Error)
        assertEquals(500, (result as NetworkResult.Error).code)
        assertNull(result.message)
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

    @Test
    fun `executeWithRetryAndWrap returns result even when logging throws exception`() = runTest {
        val expectedResponse = Response.success("Test Data")

        every {
            SyncTimeLogger.logApiCall(any(), any(), any(), any())
        } throws RuntimeException("Forced logging error")

        val actualResponse = ApiClient.executeWithRetryAndWrap {
            expectedResponse
        }

        assertNotNull(actualResponse)
        assertTrue(actualResponse!!.isSuccessful)
        assertEquals("Test Data", actualResponse.body())

        verify(exactly = 1) {
            SyncTimeLogger.logApiCall(any(), any(), any(), any())
        }
    }

    @Test
    fun `testExtractEndpointFromStackTrace Exception`() {
        mockkObject(ApiClient)
        every { ApiClient.getStackTrace() } throws RuntimeException("Mocked exception for testing")

        val method = ApiClient::class.java.getDeclaredMethod("extractEndpointFromStackTrace")
        method.isAccessible = true

        val result = method.invoke(ApiClient) as String
        assertEquals("unknown_endpoint", result)
    }
}
