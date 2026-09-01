package org.ole.planet.myplanet

import android.content.Context
import android.net.TrafficStats
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@OptIn(ExperimentalCoroutinesApi::class)
class MainApplicationTest {
    private lateinit var mockContext: Context
    private lateinit var mockEntryPoint: CoreDependenciesEntryPoint
    private lateinit var mockServerUrlMapper: ServerUrlMapper

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        MainApplication.testContext = mockContext

        mockEntryPoint = mockk(relaxed = true)
        mockServerUrlMapper = mockk(relaxed = true)

        mockkStatic(EntryPointAccessors::class)
        mockkStatic(TrafficStats::class)
        every { TrafficStats.setThreadStatsTag(any()) } returns Unit
        every { TrafficStats.clearThreadStatsTag() } returns Unit
        every { EntryPointAccessors.fromApplication(mockContext, CoreDependenciesEntryPoint::class.java) } returns mockEntryPoint
        every { mockEntryPoint.serverUrlMapper() } returns mockServerUrlMapper

        val mockMapping = mockk<ServerUrlMapper.UrlMapping>(relaxed = true)
        every { mockMapping.alternativeUrl } returns null
        every { mockServerUrlMapper.processUrl(any()) } returns mockMapping
    }

    @After
    fun tearDown() {
        MainApplication.testContext = null
        unmockkAll()
    }

    @Test
    fun `isServerReachable tests dispatcher and returns false for invalid URL`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        var result: Boolean? = null

        launch(testDispatcher) {
            result = MainApplication.isServerReachable("invalid_url", testDispatcher)
        }

        // Before advancing, the coroutine has not completed
        assert(result == null)

        // Run the dispatcher
        advanceUntilIdle()

        // Since invalid_url will throw an exception or return false
        assertFalse(result == true)
    }

    @Test
    fun `isPrimaryServerReachable returns false for invalid URL`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        var result: Boolean? = null

        launch(testDispatcher) {
            result = MainApplication.isPrimaryServerReachable("invalid_url", testDispatcher)
        }

        advanceUntilIdle()

        assertFalse(result == true)
    }

    @Test
    fun `isPrimaryServerReachable never consults the alternative URL mapping`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        launch(testDispatcher) {
            MainApplication.isPrimaryServerReachable("invalid_url", testDispatcher)
        }

        advanceUntilIdle()

        verify(exactly = 0) { mockServerUrlMapper.processUrl(any()) }
    }

    @Test
    fun `isPrimaryServerReachable uses HTTP HEAD when supported`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val receivedMethods = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            receivedMethods.add(exchange.requestMethod)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        try {
            val serverUrl = "http://localhost:${server.address.port}"
            var result: Boolean? = null
            launch(testDispatcher) {
                result = MainApplication.isPrimaryServerReachable(serverUrl, testDispatcher)
            }
            advanceUntilIdle()

            assertTrue(result == true)
            assertEquals(listOf("HEAD"), receivedMethods)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `isPrimaryServerReachable falls back to HTTP GET when HEAD returns 405`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val receivedMethods = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            receivedMethods.add(exchange.requestMethod)
            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(405, -1)
            } else {
                exchange.sendResponseHeaders(200, -1)
            }
            exchange.close()
        }
        server.start()

        try {
            val serverUrl = "http://localhost:${server.address.port}"
            var result: Boolean? = null
            launch(testDispatcher) {
                result = MainApplication.isPrimaryServerReachable(serverUrl, testDispatcher)
            }
            advanceUntilIdle()

            assertTrue(result == true)
            assertEquals(listOf("HEAD", "GET"), receivedMethods)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `isPrimaryServerReachable falls back to HTTP GET when HEAD returns 501`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val receivedMethods = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            receivedMethods.add(exchange.requestMethod)
            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(501, -1)
            } else {
                exchange.sendResponseHeaders(200, -1)
            }
            exchange.close()
        }
        server.start()

        try {
            val serverUrl = "http://localhost:${server.address.port}"
            var result: Boolean? = null
            launch(testDispatcher) {
                result = MainApplication.isPrimaryServerReachable(serverUrl, testDispatcher)
            }
            advanceUntilIdle()

            assertTrue(result == true)
            assertEquals(listOf("HEAD", "GET"), receivedMethods)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `isPrimaryServerReachable returns false without GET fallback when HEAD returns 404`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val receivedMethods = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            receivedMethods.add(exchange.requestMethod)
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()

        try {
            val serverUrl = "http://localhost:${server.address.port}"
            var result: Boolean? = null
            launch(testDispatcher) {
                result = MainApplication.isPrimaryServerReachable(serverUrl, testDispatcher)
            }
            advanceUntilIdle()

            assertFalse(result == true)
            assertEquals(listOf("HEAD"), receivedMethods)
        } finally {
            server.stop(0)
        }
    }
}
