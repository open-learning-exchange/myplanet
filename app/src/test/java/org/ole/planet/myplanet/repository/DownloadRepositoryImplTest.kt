package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryImplTest {

    private lateinit var timeProvider: TestTimeProvider
    private lateinit var diagnosticsRepository: DiagnosticsRepository

    @Before
    fun setup() {
        timeProvider = TestTimeProvider(currentTime = 123456789L)
        diagnosticsRepository = mockk(relaxed = true)
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

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Success)
        val successResult = result as DownloadResult.Success
        assertTrue(successResult.body == mockResponseBody)
    }

    @Test
    fun `downloadFileResponse sends a Range header and surfaces 206 when resuming`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "second half".toResponseBody(null)
        val mockResponse = Response.success(206, mockResponseBody)

        coEvery { mockApiInterface.downloadFile(authHeader, url, "bytes=100-") } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader, resumeOffset = 100L)

        assertTrue(result is DownloadResult.Success)
        val successResult = result as DownloadResult.Success
        assertEquals(206, successResult.code)
        coVerify { mockApiInterface.downloadFile(authHeader, url, "bytes=100-") }
    }

    @Test
    fun `downloadFileResponse sends If-Range alongside Range when a validator is available`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "second half".toResponseBody(null)
        val mockResponse = Response.success(206, mockResponseBody)

        coEvery { mockApiInterface.downloadFile(authHeader, url, "bytes=100-", "\"etag-123\"") } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader, resumeOffset = 100L, ifRange = "\"etag-123\"")

        assertTrue(result is DownloadResult.Success)
        coVerify { mockApiInterface.downloadFile(authHeader, url, "bytes=100-", "\"etag-123\"") }
    }

    @Test
    fun `downloadFileResponse omits If-Range when there is no resume offset`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "whole file".toResponseBody(null)
        val mockResponse = Response.success(mockResponseBody)

        coEvery { mockApiInterface.downloadFile(authHeader, url, null, null) } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader, ifRange = "\"stale-etag\"")

        assertTrue(result is DownloadResult.Success)
        coVerify { mockApiInterface.downloadFile(authHeader, url, null, null) }
    }

    @Test
    fun `downloadFileResponse captures the ETag from a successful response as the validator`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "whole file".toResponseBody(null)
        val mockResponse = Response.success(mockResponseBody, okhttp3.Headers.headersOf("ETag", "\"abc123\""))

        coEvery { mockApiInterface.downloadFile(authHeader, url, null, null) } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Success)
        assertEquals("\"abc123\"", (result as DownloadResult.Success).validator)
    }

    @Test
    fun `downloadFileResponse falls back to Last-Modified when there is no ETag`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "whole file".toResponseBody(null)
        val mockResponse = Response.success(
            mockResponseBody,
            okhttp3.Headers.headersOf("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
        )

        coEvery { mockApiInterface.downloadFile(authHeader, url, null, null) } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Success)
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", (result as DownloadResult.Success).validator)
    }

    @Test
    fun `downloadFileResponse omits the Range header when there is no resume offset`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()

        val url = "http://example.com/file"
        val authHeader = "auth"
        val mockResponseBody = "whole file".toResponseBody(null)
        val mockResponse = Response.success(mockResponseBody)

        coEvery { mockApiInterface.downloadFile(authHeader, url, null) } returns mockResponse

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Success)
        assertEquals(200, (result as DownloadResult.Success).code)
        coVerify { mockApiInterface.downloadFile(authHeader, url, null) }
    }

    @Test
    fun `downloadFileResponse maps 416 to a distinct error message`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        val mockResponse = Response.error<okhttp3.ResponseBody>(416, "error".toResponseBody(null))
        coEvery { mockApiInterface.downloadFile(authHeader, url, "bytes=500-") } returns mockResponse

        val result = repository.downloadFileResponse(url, authHeader, resumeOffset = 500L)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Requested range not satisfiable", (result as DownloadResult.Error).message)
        assertEquals(416, result.code)
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

        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)
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
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

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
            assertEquals("Wrong code for $code", code, result.code)
        }
    }

    @Test
    fun `downloadFileResponse handles specific network exceptions`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

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
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

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
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        coEvery { mockApiInterface.downloadFile(authHeader, url) } throws IOException()

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Network error: Unknown IO error", (result as DownloadResult.Error).message)
    }

    @Test
    fun `downloadFileResponse handles generic Exception without message`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        coEvery { mockApiInterface.downloadFile(authHeader, url) } throws RuntimeException()

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Network error: Unknown error", (result as DownloadResult.Error).message)
    }

    @Test
    fun `downloadFileResponse handles generic Exception with message`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://example.com/file"
        val authHeader = "auth"

        coEvery { mockApiInterface.downloadFile(authHeader, url) } throws RuntimeException("Test Generic Exception")

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Network error: Test Generic Exception", (result as DownloadResult.Error).message)
    }

    @Test
    fun `downloadFileResponse sanitizes URL and logs to diagnostics on 404`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://satellite:1234@example.com:5984/db/resources/r1/file.pdf?rev=2#x"
        val authHeader = "auth"

        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()
        val mockResponse = Response.error<okhttp3.ResponseBody>("".toResponseBody(null), rawResponse)

        coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("File not found", (result as DownloadResult.Error).message)
        assertEquals(404, result.code)

        val loggedUrlSlot = slot<String>()
        coVerify(exactly = 1) {
            diagnosticsRepository.saveLogToRoom("File Not Found", capture(loggedUrlSlot), "123456789")
        }
        val loggedUrl = loggedUrlSlot.captured
        assertEquals("http://example.com:5984/db/resources/r1/file.pdf", loggedUrl)
        assertFalse(loggedUrl.contains("1234"))
        assertFalse(loggedUrl.contains("satellite"))
        assertFalse(loggedUrl.contains("@"))
        assertFalse(loggedUrl.contains("?"))
    }

    @Test
    fun `downloadFileResponse returns 404 error when saveLogToRoom returns false`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://satellite:1234@example.com:5984/db/resources/r1/file.pdf?rev=2#x"
        val authHeader = "auth"

        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()
        val mockResponse = Response.error<okhttp3.ResponseBody>("".toResponseBody(null), rawResponse)

        coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse
        coEvery { diagnosticsRepository.saveLogToRoom(any(), any(), any()) } returns false

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("File not found", (result as DownloadResult.Error).message)
        assertEquals(404, result.code)
    }

    @Test
    fun `downloadFileResponse rethrows CancellationException from 404 saveLogToRoom`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://satellite:1234@example.com:5984/db/resources/r1/file.pdf?rev=2#x"
        val authHeader = "auth"

        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()
        val mockResponse = Response.error<okhttp3.ResponseBody>("".toResponseBody(null), rawResponse)

        coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse
        coEvery { diagnosticsRepository.saveLogToRoom(any(), any(), any()) } throws kotlinx.coroutines.CancellationException("Cancelled during log write")

        var exceptionThrown = false
        try {
            repository.downloadFileResponse(url, authHeader)
        } catch (e: kotlinx.coroutines.CancellationException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun `downloadFileResponse does not log to diagnostics on 500`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockDispatcherProvider = mockk<DispatcherProvider> {
            every { io } returns testDispatcher
        }
        val mockApiInterface = mockk<ApiInterface>()
        val repository = DownloadRepositoryImpl(mockApiInterface, mockDispatcherProvider, diagnosticsRepository, timeProvider)

        val url = "http://satellite:1234@example.com:5984/db/resources/r1/file.pdf?rev=2#x"
        val authHeader = "auth"

        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Server Error")
            .build()
        val mockResponse = Response.error<okhttp3.ResponseBody>("".toResponseBody(null), rawResponse)

        coEvery { mockApiInterface.downloadFile(authHeader, url) } returns mockResponse

        val result = repository.downloadFileResponse(url, authHeader)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Server error", (result as DownloadResult.Error).message)
        assertEquals(500, result.code)

        coVerify(exactly = 0) {
            diagnosticsRepository.saveLogToRoom(any(), any(), any())
        }
    }
}
