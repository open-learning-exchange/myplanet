package org.ole.planet.myplanet.services.sync

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LoginSyncManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var sharedPrefManager: SharedPrefManager
    @MockK
    private lateinit var userRepository: UserRepository
    @MockK
    private lateinit var apiInterface: ApiInterface
    @MockK(relaxed = true)
    private lateinit var listener: OnSyncListener

    private val testScope = TestScope(testDispatcher)

    private lateinit var loginSyncManager: LoginSyncManager

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)

        loginSyncManager = LoginSyncManager(
            context,
            sharedPrefManager,
            userRepository,
            apiInterface,
            testScope
        )
    }

    @Test
    fun testLogin_emptyUsername_callsOnSyncFailed() = testScope.runTest {
        loginSyncManager.login("", "password", listener)

        advanceUntilIdle()

        verify { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun testLogin_nullUsername_callsOnSyncFailed() = testScope.runTest {
        loginSyncManager.login(null, "password", listener)

        advanceUntilIdle()

        verify { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun testLogin_emptyPassword_callsOnSyncFailed() = testScope.runTest {
        loginSyncManager.login("username", "", listener)

        advanceUntilIdle()

        verify { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun testLogin_nullPassword_callsOnSyncFailed() = testScope.runTest {
        loginSyncManager.login("username", null, listener)

        advanceUntilIdle()

        verify { listener.onSyncFailed("Username and password are required.") }
    }
}
