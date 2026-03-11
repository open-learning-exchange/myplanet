package org.ole.planet.myplanet.data.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils
import java.net.URL
import java.lang.reflect.Method
import kotlinx.coroutines.test.advanceUntilIdle

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionUpdaterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockCallback: AuthSessionUpdater.AuthCallback
    private lateinit var mockSharedPrefManager: SharedPrefManager
    private lateinit var authSessionUpdater: AuthSessionUpdater

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockCallback = mockk(relaxed = true)
        mockSharedPrefManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        if (::authSessionUpdater.isInitialized) {
            authSessionUpdater.stop()
        }
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `getSessionUrl returns null when UrlUtils throws Exception`() {
        authSessionUpdater = AuthSessionUpdater(
            callback = mockCallback,
            sharedPrefManager = mockSharedPrefManager,
            scope = testScope
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
        authSessionUpdater = AuthSessionUpdater(
            callback = mockCallback,
            sharedPrefManager = mockSharedPrefManager,
            scope = testScope
        )

        every { mockSharedPrefManager.getUrlUser() } throws RuntimeException("Mocked exception")

        val getJsonObjectMethod: Method = AuthSessionUpdater::class.java.getDeclaredMethod("getJsonObject")
        getJsonObjectMethod.isAccessible = true
        val result = getJsonObjectMethod.invoke(authSessionUpdater)

        assertNull(result)
    }

    @Test
    fun `sendPost calls onError when getSessionUrl throws Exception`() = testScope.runTest {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } throws RuntimeException("Mocked getUrl exception")

        authSessionUpdater = AuthSessionUpdater(
            callback = mockCallback,
            sharedPrefManager = mockSharedPrefManager,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        Thread.sleep(100) // Allow unconfined coroutine IO dispatch to occur

        verify(timeout = 1000) { mockCallback.onError(any()) }
    }
}
