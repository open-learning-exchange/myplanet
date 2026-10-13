package org.ole.planet.myplanet.data.api

import android.app.Application
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.BroadcastService
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RetryInterceptorEventTest {

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
    fun testRetryEventUrlContainsOnlyEncodedPath() {
        val broadcastService = mockk<BroadcastService>(relaxed = true)
        val timeProvider = TestTimeProvider()
        val retryInterceptor = RetryInterceptor(broadcastService, timeProvider).apply {
            initialDelay = 10L
        }

        val request = Request.Builder()
            .url("http://satellite:1234@host:5984/db/_find?x=y")
            .post("{}".toRequestBody())
            .build()

        val response503 = createResponse(request, 503)
        val response200 = createResponse(request, 200)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response503 andThen response200
        every { chain.call() } returns notCancelledCall()

        val intentSlot = slot<Intent>()

        val result = retryInterceptor.intercept(chain)

        assertEquals(200, result.code)

        verify(exactly = 1) { broadcastService.trySendBroadcast(capture(intentSlot)) }

        val capturedIntent = intentSlot.captured
        val urlExtra = capturedIntent.getStringExtra("url")

        assertEquals("/db/_find", urlExtra)
        assertFalse(urlExtra!!.contains("1234"))
        assertFalse(urlExtra.contains("satellite"))
        assertEquals(1, capturedIntent.getIntExtra("attempt", -1))
        assertEquals(10L, capturedIntent.getLongExtra("delay", -1))
    }
}
