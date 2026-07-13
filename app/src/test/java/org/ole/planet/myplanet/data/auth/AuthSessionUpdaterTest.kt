package org.ole.planet.myplanet.data.auth

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.lang.reflect.Method
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionUpdaterTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockCallback: AuthSessionUpdater.AuthCallback
    private lateinit var mockSharedPrefManager: SharedPrefManager
    private lateinit var mockDispatcherProvider: DispatcherProvider
    private lateinit var authSessionUpdater: AuthSessionUpdater

    @Before
    fun setup() {
        mockCallback = mockk(relaxed = true)
        mockSharedPrefManager = mockk(relaxed = true)
        mockDispatcherProvider = mockk(relaxed = true)
        every { mockDispatcherProvider.io } returns mainDispatcherRule.testDispatcher
        every { mockDispatcherProvider.main } returns mainDispatcherRule.testDispatcher
        every { mockDispatcherProvider.default } returns mainDispatcherRule.testDispatcher
        every { mockDispatcherProvider.unconfined } returns mainDispatcherRule.testDispatcher
    }

    @After
    fun tearDown() {
        if (::authSessionUpdater.isInitialized) {
            authSessionUpdater.stop()
        }
        unmockkAll()
    }

    @Test
    fun `getSessionUrl returns null when UrlUtils throws Exception`() {
        val testScope = kotlinx.coroutines.test.TestScope(mainDispatcherRule.testDispatcher)
        authSessionUpdater = AuthSessionUpdater(
            callback = mockCallback,
            sharedPrefManager = mockSharedPrefManager,
            scope = testScope,
            dispatcherProvider = mockDispatcherProvider
        )

        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } throws RuntimeException("Mocked exception")

        val getSessionUrlMethod: Method = AuthSessionUpdater::class.java.getDeclaredMethod("getSessionUrl")
        getSessionUrlMethod.isAccessible = true
        val result = getSessionUrlMethod.invoke(authSessionUpdater)

        assertNull(result)
    }

    @Test
    fun `getJsonObject returns null when sharedPrefManager throws Exception`() {
        val testScope = kotlinx.coroutines.test.TestScope(mainDispatcherRule.testDispatcher)
        authSessionUpdater = AuthSessionUpdater(
            callback = mockCallback,
            sharedPrefManager = mockSharedPrefManager,
            scope = testScope,
            dispatcherProvider = mockDispatcherProvider
        )

        every { mockSharedPrefManager.getUrlUser() } throws RuntimeException("Mocked exception")

        val getJsonObjectMethod: Method = AuthSessionUpdater::class.java.getDeclaredMethod("getJsonObject")
        getJsonObjectMethod.isAccessible = true
        val result = getJsonObjectMethod.invoke(authSessionUpdater)

        assertNull(result)
    }

    @Test
    fun `sendPost calls onError when getSessionUrl throws Exception`() = runTest(mainDispatcherRule.testDispatcher) {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } throws RuntimeException("Mocked getUrl exception")

        authSessionUpdater = AuthSessionUpdater(
            callback = mockCallback,
            sharedPrefManager = mockSharedPrefManager,
            scope = this,
            dispatcherProvider = mockDispatcherProvider
        )

        testScheduler.advanceTimeBy(100L) // Or 1, as start() will try to infinitely loop until stopped.

        authSessionUpdater.stop()

        coVerify(atLeast = 1) { mockCallback.onError(any()) }
    }
}
