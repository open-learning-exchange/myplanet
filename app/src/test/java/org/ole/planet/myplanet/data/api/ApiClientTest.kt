package org.ole.planet.myplanet.data.api

import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import retrofit2.Response

class ApiClientTest {

    private fun success(body: String?): Response<String> = mockk {
        every { isSuccessful } returns true
        every { body() } returns body
        every { code() } returns 200
    }

    private fun httpError(code: Int, errorBody: String?): Response<String> = mockk {
        every { isSuccessful } returns false
        every { this@mockk.code() } returns code
        every { errorBody() } returns errorBody?.let {
            mockk<ResponseBody> { every { string() } returns it }
        }
    }

    @Test
    fun `executeWithRetryAndWrap returns success without retrying`() = runTest {
        var calls = 0
        val result = ApiClient.executeWithRetryAndWrap {
            calls++
            success("ok")
        }
        assertEquals(1, calls)
        assertTrue(result?.isSuccessful == true)
    }

    @Test
    fun `executeWithRetryAndWrap retries unsuccessful responses then succeeds`() = runTest {
        var calls = 0
        val result = ApiClient.executeWithRetryAndWrap {
            calls++
            if (calls < 2) httpError(503, null) else success("ok")
        }
        assertEquals(2, calls)
        assertTrue(result?.isSuccessful == true)
    }

    @Test
    fun `executeWithResult maps a 2xx body to Success`() = runTest {
        val result = ApiClient.executeWithResult { success("payload") }
        assertTrue(result is NetworkResult.Success)
        assertEquals("payload", (result as NetworkResult.Success).data)
    }

    @Test
    fun `executeWithResult maps a successful empty body to Error`() = runTest {
        val result = ApiClient.executeWithResult { success(null) }
        assertTrue(result is NetworkResult.Error)
        assertEquals(200, (result as NetworkResult.Error).code)
    }

    @Test
    fun `executeWithResult maps an HTTP error to Error and exhausts retries`() = runTest {
        var calls = 0
        val result = ApiClient.executeWithResult {
            calls++
            httpError(500, "boom")
        }
        assertEquals(3, calls)
        assertTrue(result is NetworkResult.Error)
        assertEquals(500, (result as NetworkResult.Error).code)
        assertEquals("boom", result.message)
    }

    @Test
    fun `executeWithResult maps a thrown exception to Exception after retries`() = runTest {
        var calls = 0
        val result = ApiClient.executeWithResult<String> {
            calls++
            throw IOException("network down")
        }
        assertEquals(3, calls)
        assertTrue(result is NetworkResult.Exception)
        assertTrue((result as NetworkResult.Exception).exception is IOException)
    }
}
