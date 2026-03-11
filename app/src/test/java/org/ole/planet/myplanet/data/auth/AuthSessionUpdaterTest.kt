package org.ole.planet.myplanet.data.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionUpdaterTest {

    private lateinit var callback: AuthSessionUpdater.AuthCallback
    private lateinit var sharedPrefManager: SharedPrefManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        callback = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        mockkObject(UrlUtils)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `sendPost exception triggers onError callback`() = runTest(testDispatcher) {
        // Arrange
        every { UrlUtils.getUrl() } throws RuntimeException("Test Exception")

        // Act
        val updater = AuthSessionUpdater(callback, sharedPrefManager, this)
        advanceUntilIdle() // Allow the coroutine to execute

        // Assert
        verify(atLeast = 1) { callback.onError(any()) }

        // Cleanup
        updater.stop()
    }
}
