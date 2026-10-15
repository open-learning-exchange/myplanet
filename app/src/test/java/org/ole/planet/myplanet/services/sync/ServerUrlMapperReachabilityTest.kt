package org.ole.planet.myplanet.services.sync

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.utils.TestDispatcherProvider

class ServerUrlMapperReachabilityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val serverUrlMapper = ServerUrlMapper(dispatcherProvider)

    @Test
    fun `isUrlDirectlyReachable sends HEAD and 200 response returns true`() = runTest(testDispatcher) {
        withLocalServer({ 200 }) { serverUrl, receivedMethods ->
            val result = serverUrlMapper.isUrlDirectlyReachable(serverUrl)
            assertTrue(result)
            assertEquals(listOf("HEAD"), receivedMethods)
        }
    }

    @Test
    fun `isUrlDirectlyReachable returns true when server answers HEAD with 405`() = runTest(testDispatcher) {
        withLocalServer({ 405 }) { serverUrl, receivedMethods ->
            val result = serverUrlMapper.isUrlDirectlyReachable(serverUrl)
            assertTrue(result)
            assertEquals(listOf("HEAD"), receivedMethods)
        }
    }

    @Test
    fun `isUrlDirectlyReachable returns true for 503 and 599 status codes`() = runTest(testDispatcher) {
        withLocalServer({ 503 }) { serverUrl503, _ ->
            assertTrue(serverUrlMapper.isUrlDirectlyReachable(serverUrl503))
        }

        withLocalServer({ 599 }) { serverUrl599, _ ->
            assertTrue(serverUrlMapper.isUrlDirectlyReachable(serverUrl599))
        }
    }

    @Test
    fun `isUrlDirectlyReachable returns false for closed port without throwing`() = runTest(testDispatcher) {
        // Create server to bind a free port, then stop it immediately to get a closed port
        val dummyServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val closedPort = dummyServer.address.port
        dummyServer.stop(0)

        val result = serverUrlMapper.isUrlDirectlyReachable("http://127.0.0.1:$closedPort")
        assertFalse(result)
    }

    @Test
    fun `isUrlDirectlyReachable prepends http to URL without scheme`() = runTest(testDispatcher) {
        withLocalServer({ 200 }) { serverUrl, receivedMethods ->
            val hostAndPort = serverUrl.removePrefix("http://")
            val result = serverUrlMapper.isUrlDirectlyReachable(hostAndPort)
            assertTrue(result)
            assertEquals(listOf("HEAD"), receivedMethods)
        }
    }

    private suspend fun withLocalServer(
        responseCode: (String) -> Int,
        block: suspend (serverUrl: String, receivedMethods: List<String>) -> Unit
    ) {
        val receivedMethods = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            receivedMethods.add(exchange.requestMethod)
            exchange.sendResponseHeaders(responseCode(exchange.requestMethod), -1)
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", receivedMethods)
        } finally {
            server.stop(0)
        }
    }
}
