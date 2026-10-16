package org.ole.planet.myplanet.di

import com.google.gson.Gson
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.api.RetryInterceptor

class NetworkModuleTest {

    private class TestModel {
        var normalField: String = "normal"
        var nullField: String? = null
        val finalField: String = "final"
        @Transient
        var transientField: String = "transient"

        companion object {
            @JvmStatic
            var staticField: String = "static"
        }
    }

    @Test
    fun `provideGson returns Gson instance with correct configuration`() {
        val gson: Gson = NetworkModule.provideGson()

        assertNotNull(gson)

        val model = TestModel()
        val json = gson.toJson(model)

        assertTrue("JSON should contain normalField", json.contains("\"normalField\":\"normal\""))
        assertTrue("JSON should contain nullField due to serializeNulls()", json.contains("\"nullField\":null"))
        assertFalse("JSON should NOT contain transientField", json.contains("transientField"))
        assertFalse("JSON should NOT contain staticField", json.contains("staticField"))
        assertFalse("JSON should NOT contain finalField", json.contains("finalField"))
    }

    @Test
    fun `provideStandardOkHttpClient returns OkHttpClient configured with ConnectionPool and Dispatcher`() {
        val mockRetryInterceptor = mockk<RetryInterceptor>(relaxed = true)
        val connectionPool = NetworkModule.provideConnectionPool()
        val okHttpClient = NetworkModule.provideStandardOkHttpClient(mockRetryInterceptor, connectionPool)

        assertNotNull(okHttpClient)
        assertEquals(20, okHttpClient.dispatcher.maxRequestsPerHost)
        assertSame(connectionPool, okHttpClient.connectionPool)
    }

    @Test
    fun `provideReachabilityOkHttpClient probes with short timeouts and no retries`() {
        val connectionPool = NetworkModule.provideConnectionPool()
        val okHttpClient = NetworkModule.provideReachabilityOkHttpClient(connectionPool)

        assertEquals(5_000, okHttpClient.connectTimeoutMillis)
        assertEquals(5_000, okHttpClient.readTimeoutMillis)
        assertSame(connectionPool, okHttpClient.connectionPool)
        assertTrue(
            "A reachability probe must not retry, or an unreachable server takes tens of seconds to report",
            okHttpClient.interceptors.none { it is RetryInterceptor }
        )
    }

    @Test
    fun `provided clients share supplied connectionPool instance but use different dispatcher instances`() {
        val mockRetryInterceptor = mockk<RetryInterceptor>(relaxed = true)
        val connectionPool = NetworkModule.provideConnectionPool()
        val standardClient = NetworkModule.provideStandardOkHttpClient(mockRetryInterceptor, connectionPool)
        val reachabilityClient = NetworkModule.provideReachabilityOkHttpClient(connectionPool)

        assertSame(connectionPool, standardClient.connectionPool)
        assertSame(connectionPool, reachabilityClient.connectionPool)
        assertSame(standardClient.connectionPool, reachabilityClient.connectionPool)
        assertNotSame(standardClient.dispatcher, reachabilityClient.dispatcher)
    }
}
