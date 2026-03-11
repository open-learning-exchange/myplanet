package org.ole.planet.myplanet.data.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils

class AuthSessionUpdaterTest {

    private lateinit var authCallback: AuthSessionUpdater.AuthCallback
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var scope: CoroutineScope
    private lateinit var authSessionUpdater: AuthSessionUpdater

    @Before
    fun setup() {
        authCallback = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        scope = CoroutineScope(Dispatchers.Unconfined + Job())

        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mockurl"

        every { sharedPrefManager.getUrlUser() } returns "user"
        every { sharedPrefManager.getUrlPwd() } returns "pwd"

        authSessionUpdater = AuthSessionUpdater(authCallback, sharedPrefManager, scope)
    }

    @After
    fun tearDown() {
        if (::authSessionUpdater.isInitialized) {
            authSessionUpdater.stop()
        }
        unmockkAll()
    }

    @Test
    fun `getJsonObject returns null when exception occurs`() {
        // Arrange
        every { sharedPrefManager.getUrlUser() } throws RuntimeException("Mocked exception")

        // Act
        val getJsonObjectMethod = AuthSessionUpdater::class.java.getDeclaredMethod("getJsonObject")
        getJsonObjectMethod.isAccessible = true
        val result = getJsonObjectMethod.invoke(authSessionUpdater)

        // Assert
        assertNull(result)
    }

    @Test
    fun `getSessionUrl returns null when exception occurs`() {
        // Arrange
        every { UrlUtils.getUrl() } throws RuntimeException("Mocked exception")

        // Act
        val getSessionUrlMethod = AuthSessionUpdater::class.java.getDeclaredMethod("getSessionUrl")
        getSessionUrlMethod.isAccessible = true
        val result = getSessionUrlMethod.invoke(authSessionUpdater)

        // Assert
        assertNull(result)
    }
}
