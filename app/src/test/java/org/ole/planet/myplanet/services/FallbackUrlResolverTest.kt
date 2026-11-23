package org.ole.planet.myplanet.services

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FallbackUrlResolverTest {

    private val url = "http://192.168.1.73/db/resources/abc/photo.png?rev=2"
    private val serverUrlMapper = mockk<ServerUrlMapper>()
    private val sharedPrefManager = mockk<SharedPrefManager>()
    private val resolver = FallbackUrlResolver(serverUrlMapper, sharedPrefManager)

    @Test
    fun `a hardcoded mapping wins and keeps the path and query`() {
        every { serverUrlMapper.processUrl(url) } returns
            ServerUrlMapper.UrlMapping(url, alternativeUrl = "https://planet.example.org", extractedBaseUrl = "http://192.168.1.73")
        every { sharedPrefManager.getProcessedAlternativeUrl() } returns "https://stored.example.org/"

        assertEquals(
            FallbackUrlResolver.Fallback("http://192.168.1.73", "https://planet.example.org/db/resources/abc/photo.png?rev=2"),
            resolver.resolve(url)
        )
    }

    @Test
    fun `without a mapping the stored alternative is used, trailing slash trimmed`() {
        every { serverUrlMapper.processUrl(url) } returns
            ServerUrlMapper.UrlMapping(url, extractedBaseUrl = "http://192.168.1.73")
        every { sharedPrefManager.getProcessedAlternativeUrl() } returns "https://stored.example.org/"

        assertEquals("https://stored.example.org/db/resources/abc/photo.png?rev=2", resolver.resolve(url)?.alternativeUrl)
    }

    @Test
    fun `no mapping and nothing stored means no fallback`() {
        every { serverUrlMapper.processUrl(url) } returns
            ServerUrlMapper.UrlMapping(url, extractedBaseUrl = "http://192.168.1.73")
        every { sharedPrefManager.getProcessedAlternativeUrl() } returns ""

        assertNull(resolver.resolve(url))
    }
}
