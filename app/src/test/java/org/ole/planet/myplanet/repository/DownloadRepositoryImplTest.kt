package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryImplTest {

    @Test
    fun `downloadFileResponse uses dispatcher and returns success`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "test content".toResponseBody(null)
        val mockResponse = Response.success(mockResponseBody)

        coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider)

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Success)
        val successResult = result as DownloadResult.Success
        assertTrue((result as DownloadResult.Success).body == mockResponseBody)
    }
}
