package org.ole.planet.myplanet.services.sync

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager

@OptIn(ExperimentalCoroutinesApi::class)
class LoginSyncManagerTest {

    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var sharedPrefManager: SharedPrefManager
    @MockK
    private lateinit var userRepository: UserRepository
    @MockK
    private lateinit var apiInterface: ApiInterface
    @MockK
    private lateinit var listener: OnSyncListener

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var loginSyncManager: LoginSyncManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this, relaxed = true)

        loginSyncManager = LoginSyncManager(
            context,
            sharedPrefManager,
            userRepository,
            apiInterface,
            testScope
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLogin_emptyUsername_callsOnSyncFailed() = runTest {
        loginSyncManager.login("", "password", listener)

        verify(timeout = 1000) { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun testLogin_nullUsername_callsOnSyncFailed() = runTest {
        loginSyncManager.login(null, "password", listener)

        verify(timeout = 1000) { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun testLogin_emptyPassword_callsOnSyncFailed() = runTest {
        loginSyncManager.login("username", "", listener)

        verify(timeout = 1000) { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun testLogin_nullPassword_callsOnSyncFailed() = runTest {
        loginSyncManager.login("username", null, listener)

        verify(timeout = 1000) { listener.onSyncFailed("Username and password are required.") }
    }
}
