package org.ole.planet.myplanet.data.api

import android.content.Intent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.service.BroadcastService
import java.util.concurrent.TimeUnit

class RetryInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var broadcastService: FakeBroadcastService
    private lateinit var okHttpClient: OkHttpClient

    class FakeBroadcastService : BroadcastService() {
        var callCount = 0
        override suspend fun sendBroadcast(intent: Intent) {
            callCount++
        }
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        broadcastService = FakeBroadcastService()

        val retryInterceptor = RetryInterceptor(broadcastService)
        retryInterceptor.initialDelay = 10L

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testRetryOn500Error() {
        // Enqueue 3 failures (500) and then 1 success (200)
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Success"))

        val request = Request.Builder()
            .url(mockWebServer.url("/"))
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("Success", response.body?.string())

        assertEquals(3, broadcastService.callCount)
        assertEquals(4, mockWebServer.requestCount)
    }

    @Test
    fun testMaxRetriesExceeded() {
        // Enqueue 4 failures (500)
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val request = Request.Builder()
            .url(mockWebServer.url("/"))
            .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(500, response.code)

        assertEquals(3, broadcastService.callCount)
        assertEquals(4, mockWebServer.requestCount)
    }
}
