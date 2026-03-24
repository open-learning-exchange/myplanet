package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryImplTest {

    @Before
    fun setup() {
        mockkObject(MainApplication)
        every { MainApplication.createLog(any(), any()) } just runs
    }

    @After
    fun teardown() {
        unmockkObject(MainApplication)
    }

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

    @Test
    fun `downloadFileResponse returns empty body error`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponse = Response.success<okhttp3.ResponseBody>(null)

        coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider)
        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Empty response body", (result as DownloadResult.Error).message)
    }

    @Test
    fun `downloadFileResponse handles mapped HTTP error codes`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        val errorCases = mapOf(
            401 to "Unauthorized access",
            403 to "Forbidden - Access denied",
            404 to "File not found",
            408 to "Request timeout",
            500 to "Server error",
            502 to "Bad gateway",
            503 to "Service unavailable",
            504 to "Gateway timeout",
            418 to "Connection failed (418)"
        )

        for ((code, expectedMessage) in errorCases) {
            val mockResponse = Response.error<okhttp3.ResponseBody>(
                code,
                "error".toResponseBody(null)
            )
            coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse

            val result = repository.downloadFileResponse(url, authHeader)

            assertTrue("Expected Error for code $code", result is DownloadResult.Error)
            assertEquals("Wrong message for code $code", expectedMessage, (result as DownloadResult.Error).message)
            assertEquals("Wrong code for $code", code, (result as DownloadResult.Error).code)
        }
    }

    @Test
    fun `downloadFileResponse handles specific network exceptions`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        val exceptions = listOf(
            UnknownHostException() to "Server not reachable. Check internet connection.",
            SocketTimeoutException() to "Connection timeout. Please try again.",
            ConnectException() to "Unable to connect to server"
        )

        for ((exception, expectedMessage) in exceptions) {
            coEvery { mockApiInterface.downloadFile(authHeader, url) } throws exception

            val result = repository.downloadFileResponse(url, authHeader)

            assertTrue("Expected Error for ${exception.javaClass.simpleName}", result is DownloadResult.Error)
            assertEquals("Wrong message for ${exception.javaClass.simpleName}", expectedMessage, (result as DownloadResult.Error).message)
        }
    }

    @Test
    fun `downloadFileResponse handles generic IOException with message`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        coEvery { mockApiInterface.downloadFile(authHeader, url) } throws IOException("Test IO Exception")

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Network error: Test IO Exception", (result as DownloadResult.Error).message)
    }

    @Test
    fun `downloadFileResponse handles generic IOException without message`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        coEvery { mockApiInterface.downloadFile(authHeader, url) } throws IOException()

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Network error: Unknown IO error", (result as DownloadResult.Error).message)
    }
}
