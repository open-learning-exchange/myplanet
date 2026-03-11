package org.ole.planet.myplanet.data.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import retrofit2.Response

class ApiClientTest {

    @Test
    fun `executeWithResult returns Error with body when response is unsuccessful and has errorBody`() = runTest {
        val errorJson = "{\"error\": \"Not found\"}"
        val errorResponseBody = errorJson.toResponseBody("application/json".toMediaTypeOrNull())
        val mockResponse = Response.error<Any>(404, errorResponseBody)

        val operation: suspend () -> Response<Any>? = { mockResponse }

        val result = ApiClient.executeWithResult(operation)

        assertTrue(result is NetworkResult.Error)
        assertEquals(404, (result as NetworkResult.Error).code)
        assertEquals(errorJson, (result as NetworkResult.Error).message)
    }

    @Test
    fun `executeWithResult returns Error with null message when errorBody throws exception`() = runTest {
        val errorResponseBody = mockk<ResponseBody>()
        every { errorResponseBody.contentType() } returns "application/json".toMediaTypeOrNull()
        every { errorResponseBody.contentLength() } returns 100L
        every { errorResponseBody.string() } throws RuntimeException("Failed to read error body")

        val mockResponse = Response.error<Any>(500, errorResponseBody)

        val operation: suspend () -> Response<Any>? = { mockResponse }

        val result = ApiClient.executeWithResult(operation)

        assertTrue(result is NetworkResult.Error)
        assertEquals(500, (result as NetworkResult.Error).code)
        assertEquals(null, (result as NetworkResult.Error).message)
    }

    @Test
    fun `executeWithResult returns Success when response is successful with body`() = runTest {
        val successBody = "success_data"
        val mockResponse = Response.success(successBody)

        val operation: suspend () -> Response<String>? = { mockResponse }

        val result = ApiClient.executeWithResult(operation)

        assertTrue(result is NetworkResult.Success)
        assertEquals(successBody, (result as NetworkResult.Success).data)
    }

    @Test
    fun `executeWithResult returns Error with null message when response is successful but body is null`() = runTest {
        val mockResponse = Response.success<String>(204, null)

        val operation: suspend () -> Response<String>? = { mockResponse }

        val result = ApiClient.executeWithResult(operation)

        assertTrue(result is NetworkResult.Error)
        assertEquals(204, (result as NetworkResult.Error).code)
        assertEquals(null, (result as NetworkResult.Error).message)
    }
}
