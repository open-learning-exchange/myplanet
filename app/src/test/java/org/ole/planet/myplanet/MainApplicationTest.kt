package org.ole.planet.myplanet

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.utils.ServerReachabilityProvider

@OptIn(ExperimentalCoroutinesApi::class)
class MainApplicationTest {
    private companion object {
        // MainApplication caches its entry point in a lazy, so every test has to share one instance.
        val mockContext: Context = mockk(relaxed = true)
        val mockEntryPoint: CoreDependenciesEntryPoint = mockk(relaxed = true)
        val mockReachabilityProvider: ServerReachabilityProvider = mockk(relaxed = true)
    }

    @Before
    fun setup() {
        MainApplication.testContext = mockContext
        clearMocks(mockEntryPoint, mockReachabilityProvider)

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(mockContext, CoreDependenciesEntryPoint::class.java) } returns mockEntryPoint
        every { mockEntryPoint.serverReachabilityProvider() } returns mockReachabilityProvider
    }

    @After
    fun tearDown() {
        MainApplication.testContext = null
        unmockkAll()
    }

    @Test
    fun `isServerReachable delegates to the shared reachability provider`() = runTest {
        val url = "http://example.com"
        coEvery { mockReachabilityProvider.isServerReachable(url) } returns true

        assertTrue(MainApplication.isServerReachable(url))
        coVerify(exactly = 1) { mockReachabilityProvider.isServerReachable(url) }
    }

    @Test
    fun `isServerReachable propagates an unreachable server`() = runTest {
        val url = "http://example.com"
        coEvery { mockReachabilityProvider.isServerReachable(url) } returns false

        assertFalse(MainApplication.isServerReachable(url))
    }

    @Test
    fun `isPrimaryServerReachable delegates to the shared reachability provider`() = runTest {
        val url = "http://example.com"
        coEvery { mockReachabilityProvider.isPrimaryServerReachable(url) } returns true

        assertTrue(MainApplication.isPrimaryServerReachable(url))
        coVerify(exactly = 1) { mockReachabilityProvider.isPrimaryServerReachable(url) }
        coVerify(exactly = 0) { mockReachabilityProvider.isServerReachable(any()) }
    }
}
