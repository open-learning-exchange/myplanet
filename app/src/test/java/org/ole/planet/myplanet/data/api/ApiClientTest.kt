package org.ole.planet.myplanet.data.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import retrofit2.Response

class ApiClientTest {

    @Test
    fun `executeWithResult returns Error with null body when errorBody string throws exception`() = runTest {
        val errorBody = mockk<ResponseBody>()
        val response = mockk<Response<Any>>()

        // Setup a response that is not successful and will iterate 3 times.
        // We can just mock the same response each time.
        every { response.isSuccessful } returns false
        every { response.code() } returns 500
        every { response.errorBody() } returns errorBody

        // Throw exception when trying to read the string
        every { errorBody.string() } throws RuntimeException("Failed to read body")

        val result = ApiClient.executeWithResult { response }

        assert(result is NetworkResult.Error)
        val errorResult = result as NetworkResult.Error
        assertEquals(500, errorResult.code)
        assertNull(errorResult.message)
    }
}
