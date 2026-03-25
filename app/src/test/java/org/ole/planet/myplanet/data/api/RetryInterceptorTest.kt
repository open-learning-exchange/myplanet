package org.ole.planet.myplanet.data.api

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
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
            // The interrupted status is cleared by runBlocking/delay
            Thread.interrupted() // Clear any remaining interrupted status just in case
        }
    }
}
