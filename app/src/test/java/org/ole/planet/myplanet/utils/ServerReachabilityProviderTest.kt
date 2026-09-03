package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

class ServerReachabilityProviderTest {

    private val okHttpClient: OkHttpClient = mockk()
    private val serverUrlMapper: ServerUrlMapper = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val timeProvider = TestTimeProvider(currentTime = 100_000L)

    private lateinit var provider: ServerReachabilityProvider

    @Before
    fun setUp() {
        provider = ServerReachabilityProvider(
            okHttpClient,
            serverUrlMapper,
            dispatcherProvider,
            timeProvider
        )
    }

    @Test
    fun `isServerReachable returns false for blank url`() = runTest(testDispatcher) {
        val result = provider.isServerReachable("")
        assertFalse(result)
        verify(exactly = 0) { serverUrlMapper.processUrl(any()) }
    }

    @Test
    fun `isServerReachable caches successful reachability result within TTL`() = runTest(testDispatcher) {
        val url = "http://example.com"
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk(relaxed = true)

        every { serverUrlMapper.processUrl(url) } returns ServerUrlMapper.UrlMapping(url, null)
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true

        val firstCall = provider.isServerReachable(url)
        assertTrue(firstCall)

        // Advance time by 10 seconds (within 30s TTL)
        timeProvider.advanceBy(10_000L)

        val secondCall = provider.isServerReachable(url)
        assertTrue(secondCall)

        // OkHttpClient call should execute only once due to cache hit
        verify(exactly = 1) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `isServerReachable re-evaluates after cache TTL expires`() = runTest(testDispatcher) {
        val url = "http://example.com"
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk(relaxed = true)

        every { serverUrlMapper.processUrl(url) } returns ServerUrlMapper.UrlMapping(url, null)
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true

        val firstCall = provider.isServerReachable(url)
        assertTrue(firstCall)

        // Advance time beyond 30s TTL (e.g., 30,001 ms)
        timeProvider.advanceBy(30_001L)

        val secondCall = provider.isServerReachable(url)
        assertTrue(secondCall)

        // OkHttpClient should be called twice because cache expired
        verify(exactly = 2) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `isServerReachable falls back to alternative URL if primary fails`() = runTest(testDispatcher) {
        val url = "http://primary.com"
        val altUrl = "http://alt.com"
        val mockCallPrimary: Call = mockk()
        val mockCallAlt: Call = mockk()
        val mockResponsePrimary: Response = mockk(relaxed = true)
        val mockResponseAlt: Response = mockk(relaxed = true)

        every { serverUrlMapper.processUrl(url) } returns ServerUrlMapper.UrlMapping(url, altUrl)
        every { okHttpClient.newCall(match { it.url.toString() == url || it.url.toString() == "$url/" }) } returns mockCallPrimary
        every { okHttpClient.newCall(match { it.url.toString() == altUrl || it.url.toString() == "$altUrl/" }) } returns mockCallAlt

        every { mockCallPrimary.execute() } returns mockResponsePrimary
        every { mockResponsePrimary.isSuccessful } returns false

        every { mockCallAlt.execute() } returns mockResponseAlt
        every { mockResponseAlt.isSuccessful } returns true

        val result = provider.isServerReachable(url)
        assertTrue(result)
        verify(exactly = 1) { mockCallPrimary.execute() }
        verify(exactly = 1) { mockCallAlt.execute() }
    }

    @Test
    fun `isPrimaryServerReachable does not use cache`() = runTest(testDispatcher) {
        val url = "http://example.com"
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk(relaxed = true)

        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true

        val firstCall = provider.isPrimaryServerReachable(url)
        val secondCall = provider.isPrimaryServerReachable(url)

        assertTrue(firstCall)
        assertTrue(secondCall)

        verify(exactly = 2) { okHttpClient.newCall(any()) }
    }
}
