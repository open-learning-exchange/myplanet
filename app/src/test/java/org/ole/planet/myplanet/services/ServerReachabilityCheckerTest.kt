package org.ole.planet.myplanet.services

import android.net.TrafficStats
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@OptIn(ExperimentalCoroutinesApi::class)
class ServerReachabilityCheckerTest {

    @Before
    fun setup() {
        mockkStatic(TrafficStats::class)
        every { TrafficStats.setThreadStatsTag(any()) } returns Unit
        every { TrafficStats.clearThreadStatsTag() } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun checkerWithMapper(mapper: ServerUrlMapper): ServerReachabilityChecker {
        val entryPoint = mockk<CoreDependenciesEntryPoint>(relaxed = true)
        every { entryPoint.serverUrlMapper() } returns mapper
        return ServerReachabilityChecker { entryPoint }
    }

    @Test
    fun `isServerReachable returns false for a blank url without checking the server`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val checker = checkerWithMapper(mockk(relaxed = true))

        var result: Boolean? = null
        launch(testDispatcher) { result = checker.isServerReachable("", testDispatcher) }
        advanceUntilIdle()

        assertEquals(false, result)
    }

    @Test
    fun `isServerReachable caches a reachable result within the TTL`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        try {
            val serverUrl = "http://localhost:${server.address.port}"
            val mapper = mockk<ServerUrlMapper>()
            every { mapper.processUrl(serverUrl) } returns ServerUrlMapper.UrlMapping(serverUrl, null)
            val checker = checkerWithMapper(mapper)

            launch(testDispatcher) {
                checker.isServerReachable(serverUrl, testDispatcher)
                checker.isServerReachable(serverUrl, testDispatcher)
            }
            advanceUntilIdle()

            assertEquals(1, requestCount.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `isServerReachable falls back to the alternative URL when the primary is unreachable`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val altServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        altServer.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        altServer.start()

        try {
            val primaryUrl = "http://localhost:1"
            val altUrl = "http://localhost:${altServer.address.port}"
            val mapper = mockk<ServerUrlMapper>()
            every { mapper.processUrl(primaryUrl) } returns ServerUrlMapper.UrlMapping(primaryUrl, altUrl)
            val checker = checkerWithMapper(mapper)

            var result: Boolean? = null
            launch(testDispatcher) { result = checker.isServerReachable(primaryUrl, testDispatcher) }
            advanceUntilIdle()

            assertTrue(result == true)
        } finally {
            altServer.stop(0)
        }
    }

    @Test
    fun `isPrimaryServerReachable does not use the reachability cache`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        try {
            val serverUrl = "http://localhost:${server.address.port}"
            val checker = checkerWithMapper(mockk(relaxed = true))

            launch(testDispatcher) {
                checker.isPrimaryServerReachable(serverUrl, testDispatcher)
                checker.isPrimaryServerReachable(serverUrl, testDispatcher)
            }
            advanceUntilIdle()

            assertEquals(2, requestCount.get())
        } finally {
            server.stop(0)
        }
    }
}
