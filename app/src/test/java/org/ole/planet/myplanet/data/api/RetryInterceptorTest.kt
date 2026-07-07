package org.ole.planet.myplanet.data.api

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.BroadcastService
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class RetryInterceptorTest {
    private lateinit var broadcastService: BroadcastService
    private lateinit var retryInterceptor: RetryInterceptor

    @Before
    fun setUp() {
        broadcastService = mockk(relaxed = true)
        retryInterceptor = RetryInterceptor(broadcastService)
        retryInterceptor.initialDelay = 10L
    }

    private fun notCancelledCall(): Call = mockk { every { isCanceled() } returns false }

    private fun createResponse(request: Request, code: Int): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .build()
    }

    @Test
    fun testSuccessfulResponseNoRetries() {
        val request = Request.Builder().url("http://example.com").build()
        val response = createResponse(request, 200)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        val result = retryInterceptor.intercept(chain)

        assertEquals(200, result.code)
        verify(exactly = 1) { chain.proceed(request) }
        verify(exactly = 0) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testRetriesUpToMaxRetriesOn5xxError() {
        val request = Request.Builder().url("http://example.com").build()
        val errorResponse = createResponse(request, 500)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns errorResponse
        every { chain.call() } returns notCancelledCall()

        val result = retryInterceptor.intercept(chain)

        assertEquals(500, result.code)
        // 1 initial + 3 retries = 4
        verify(exactly = 4) { chain.proceed(request) }
        verify(exactly = 3) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testSuccessAfterOneRetry() {
        val request = Request.Builder().url("http://example.com").build()
        val errorResponse = createResponse(request, 500)
        val successResponse = createResponse(request, 200)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(errorResponse, successResponse)
        every { chain.call() } returns notCancelledCall()

        val result = retryInterceptor.intercept(chain)

        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
        verify(exactly = 1) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testRetriesUpToMaxRetriesOnIOException() {
        val request = Request.Builder().url("http://example.com").build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketTimeoutException("timeout")
        every { chain.call() } returns notCancelledCall()

        try {
            retryInterceptor.intercept(chain)
            fail("Expected IOException after retries are exhausted")
        } catch (e: IOException) {
            assertTrue(e is SocketTimeoutException)
        }

        // 1 initial + 3 retries = 4
        verify(exactly = 4) { chain.proceed(request) }
        verify(exactly = 3) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testSuccessAfterIOException() {
        val request = Request.Builder().url("http://example.com").build()
        val successResponse = createResponse(request, 200)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketTimeoutException("timeout") andThen successResponse
        every { chain.call() } returns notCancelledCall()

        val result = retryInterceptor.intercept(chain)

        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
        verify(exactly = 1) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testDocumentCreatingPostIsNotRetriedOnIOException() {
        val request = Request.Builder()
            .url("http://example.com/submissions")
            .post("{}".toRequestBody())
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketTimeoutException("timeout")
        every { chain.call() } returns notCancelledCall()

        try {
            retryInterceptor.intercept(chain)
            fail("Expected IOException without retries")
        } catch (e: IOException) {
            assertTrue(e is SocketTimeoutException)
        }

        verify(exactly = 1) { chain.proceed(request) }
        verify(exactly = 0) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testDocumentCreatingPostIsNotRetriedOn5xx() {
        val request = Request.Builder()
            .url("http://example.com/submissions")
            .post("{}".toRequestBody())
            .build()
        val errorResponse = createResponse(request, 502)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns errorResponse
        every { chain.call() } returns notCancelledCall()

        val result = retryInterceptor.intercept(chain)

        assertEquals(502, result.code)
        verify(exactly = 1) { chain.proceed(request) }
        verify(exactly = 0) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testReadOnlyFindPostIsStillRetried() {
        val request = Request.Builder()
            .url("http://example.com/submissions/_find")
            .post("{}".toRequestBody())
            .build()
        val successResponse = createResponse(request, 200)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketTimeoutException("timeout") andThen successResponse
        every { chain.call() } returns notCancelledCall()

        val result = retryInterceptor.intercept(chain)

        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
        verify(exactly = 1) { broadcastService.trySendBroadcast(any()) }
    }

    @Test
    fun testInterruptedExceptionDuringDelay() {
        val request = Request.Builder().url("http://example.com").build()
        val errorResponse = createResponse(request, 500)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns errorResponse
        every { chain.call() } returns notCancelledCall()

        // Use a longer delay to ensure the other thread has time to interrupt
        retryInterceptor.initialDelay = 2000L

        val currentThread = Thread.currentThread()
        val interrupterThread = Thread {
            Thread.sleep(100)
            currentThread.interrupt()
        }
        interrupterThread.start()

        try {
            retryInterceptor.intercept(chain)
            fail("Expected IOException due to interruption")
        } catch (e: IOException) {
            assertEquals("Interrupted during retry delay", e.message)
            // Thread.interrupted() returns true if the thread was interrupted,
            // and clears the interrupted status so subsequent tests aren't affected.
            assertTrue(Thread.interrupted())
        }
    }

    @Test
    fun testCancelledCallAbortsBackoffWithoutBlocking() {
        val request = Request.Builder().url("http://example.com").build()
        val errorResponse = createResponse(request, 500)

        val cancelledCall = mockk<Call> { every { isCanceled() } returns true }
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns errorResponse
        every { chain.call() } returns cancelledCall

        retryInterceptor.initialDelay = 60_000L

        val start = System.currentTimeMillis()
        try {
            retryInterceptor.intercept(chain)
            fail("Expected IOException because the call was cancelled")
        } catch (e: IOException) {
            assertEquals("Call cancelled during retry delay", e.message)
        }
        val elapsed = System.currentTimeMillis() - start

        verify(exactly = 1) { chain.proceed(request) }
        assertTrue("Backoff should not have slept for the full delay", elapsed < 5_000L)
    }
}
