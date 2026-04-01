package org.ole.planet.myplanet

import android.content.Context
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.ServerUrlMapperEntryPoint
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@OptIn(ExperimentalCoroutinesApi::class)
class MainApplicationTest {

    private lateinit var mockContext: Context
    private lateinit var mockEntryPoint: ServerUrlMapperEntryPoint
    private lateinit var mockServerUrlMapper: ServerUrlMapper

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        MainApplication.context = mockContext

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        mockEntryPoint = mockk(relaxed = true)
        mockServerUrlMapper = mockk(relaxed = true)

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(mockContext, ServerUrlMapperEntryPoint::class.java) } returns mockEntryPoint
        every { mockEntryPoint.serverUrlMapper() } returns mockServerUrlMapper

        val mockMapping = mockk<ServerUrlMapper.UrlMapping>(relaxed = true)
        every { mockMapping.alternativeUrl } returns null
        every { mockServerUrlMapper.processUrl(any()) } returns mockMapping
    }

    @After
    fun tearDown() {
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
}
