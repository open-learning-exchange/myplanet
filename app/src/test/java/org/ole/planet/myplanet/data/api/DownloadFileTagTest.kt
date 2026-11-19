package org.ole.planet.myplanet.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit

class DownloadFileTagTest {

    @Test
    fun `downloadFile carries the NoNetworkRetry tag onto the OkHttp request only when passed`() = runBlocking {
        val seen = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                seen += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody(null))
                    .build()
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl("http://example.com/")
            .client(client)
            .build()
            .create(ApiInterface::class.java)

        api.downloadFile("auth", "http://example.com/file", noNetworkRetry = NoNetworkRetry)
        api.downloadFile("auth", "http://example.com/file")

        assertEquals(NoNetworkRetry, seen[0].tag(NoNetworkRetry::class.java))
        assertNull(seen[1].tag(NoNetworkRetry::class.java))
    }
}
