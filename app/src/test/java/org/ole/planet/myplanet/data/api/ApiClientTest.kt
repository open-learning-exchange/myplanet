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

class ApiClientTest {

    @Test
    fun testExecuteWithResult_errorBodyStringThrowsException() = runTest {
        val mockResponse = mockk<Response<Any>>()
        val mockErrorBody = mockk<ResponseBody>()

        every { mockResponse.isSuccessful } returns false
        every { mockResponse.errorBody() } returns mockErrorBody
        every { mockResponse.code() } returns 500
        every { mockErrorBody.string() } throws RuntimeException("Mock exception")

        val result = ApiClient.executeWithResult { mockResponse }

        assertTrue(result is NetworkResult.Error)
        assertEquals(500, (result as NetworkResult.Error).code)
        assertEquals(null, result.message)
    }
}
