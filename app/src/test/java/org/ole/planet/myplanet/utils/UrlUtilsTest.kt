package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.EntryPointAccessors
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class UrlUtilsTest {

    private lateinit var mockSpm: SharedPrefManager
    private lateinit var mockEntryPoint: AutoSyncEntryPoint

    @Before
    fun setUp() {
        mockSpm = mockk(relaxed = true)
        mockEntryPoint = mockk()
        every { mockEntryPoint.sharedPrefManager() } returns mockSpm

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), AutoSyncEntryPoint::class.java) } returns mockEntryPoint

        // Mock MainApplication context
        mockkObject(MainApplication.Companion)
        val mockContext = mockk<Context>()
        every { MainApplication.context } returns mockContext
    }

    @After
    fun tearDown() {
        unmockkStatic(EntryPointAccessors::class)
        unmockkObject(MainApplication.Companion)
        unmockkAll()
    }

    @Test
    fun `hostUrl fallback behavior when toUri throws Exception`() {
        // Setup shared preferences for fallback
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "fallback.org"
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns "invalid://url"

        // Instead of mocking toUri(), mock Uri.parse which toUri() calls internally
        mockkStatic(Uri::class)
        every { Uri.parse("invalid://url") } throws RuntimeException("Simulated URI Exception")

        val result = UrlUtils.hostUrl

        assertEquals("http://fallback.org/ml/", result)
    }

    @Test
    fun `hostUrl successfully returns alternative URL`() {
        // Setup shared preferences for fallback
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "fallback.org"
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns "https://newhost.com"

        val result = UrlUtils.hostUrl

        assertEquals("https://newhost.com:5000/", result)
    }

    @Test
    fun `hostUrl successfully returns standard URL`() {
        // Setup shared preferences for fallback
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "standard.org"
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getProcessedAlternativeUrl() } returns ""

        val result = UrlUtils.hostUrl

        assertEquals("http://standard.org/ml/", result)
    }

    @Test
    fun `hostUrl successfully returns URL when alternativeUrl is true but value is empty`() {
        // Setup shared preferences for fallback
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "fallback.org"
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns ""

        val result = UrlUtils.hostUrl

        assertEquals("http://fallback.org/ml/", result)
    }
}
