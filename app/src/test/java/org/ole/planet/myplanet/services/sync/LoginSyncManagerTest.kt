package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LoginSyncManagerTest {

    private lateinit var loginSyncManager: LoginSyncManager
    private val context: Context = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val listener: OnSyncListener = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }

        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mockurl"
        every { UrlUtils.header } returns "Basic mockheader"

        mockkObject(AndroidDecrypter.Companion)
        every { AndroidDecrypter.androidDecrypter(any(), any(), any(), any()) } returns true

        loginSyncManager = LoginSyncManager(
            context,
            sharedPrefManager,
            userRepository,
            apiInterface,
            testScope,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `login with empty username or password fails immediately`() = runTest {
        loginSyncManager.login("", "password", listener)
        verify { listener.onSyncFailed("Username and password are required.") }

        loginSyncManager.login("username", null, listener)
        verify { listener.onSyncFailed("Username and password are required.") }
    }

    @Test
    fun `login handles 401 error`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.error(401, "".toResponseBody("application/json".toMediaTypeOrNull()))

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Name or password is incorrect.") }
    }

    @Test
    fun `login handles 404 error`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.error(404, "".toResponseBody("application/json".toMediaTypeOrNull()))

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("User not found.") }
    }

    @Test
    fun `login handles 500 error`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.error(500, "".toResponseBody("application/json".toMediaTypeOrNull()))

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Server error. Please try again later.") }
    }

    @Test
    fun `login handles empty response`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.success(null)

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Empty response from server.") }
    }

    @Test
    fun `login handles missing auth data`() = runTest {
        val jsonDoc = JsonObject() // No derived_key or salt
        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.success(jsonDoc)

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Server response missing authentication data.") }
    }

    @Test
    fun `login with valid credentials and manager role`() = runTest {
        val jsonDoc = JsonObject()
        jsonDoc.addProperty("derived_key", "test_derived_key")
        jsonDoc.addProperty("salt", "test_salt")
        val roles = JsonArray()
        roles.add("manager")
        jsonDoc.add("roles", roles)

        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.success(jsonDoc)

        every { AndroidDecrypter.androidDecrypter("testUser", "testPass", "test_derived_key", "test_salt") } returns true
        coEvery { userRepository.saveUser(any(), any(), any(), any()) } returns mockk(relaxed = true)

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncStarted() }
        verify { listener.onSyncComplete() }
    }

    @Test
    fun `login with valid credentials but not manager`() = runTest {
        val jsonDoc = JsonObject()
        jsonDoc.addProperty("derived_key", "test_derived_key")
        jsonDoc.addProperty("salt", "test_salt")

        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.success(jsonDoc)
        every { AndroidDecrypter.androidDecrypter(any(), any(), any(), any()) } returns true
        every { context.getString(R.string.user_verification_in_progress) } returns "Verification in progress"

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Verification in progress") }
    }

    @Test
    fun `login with invalid credentials`() = runTest {
        val jsonDoc = JsonObject()
        jsonDoc.addProperty("derived_key", "test_derived_key")
        jsonDoc.addProperty("salt", "test_salt")

        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.success(jsonDoc)
        every { AndroidDecrypter.androidDecrypter(any(), any(), any(), any()) } returns false

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Authentication failed. Invalid credentials.") }
    }

    @Test
    fun `login handles network error`() = runTest {
        val exception = object : java.net.UnknownHostException() {
            override fun printStackTrace() {
                // Do nothing to avoid polluting test logs
            }
        }
        coEvery { apiInterface.getJsonObject(any(), any()) } throws exception

        loginSyncManager.login("testUser", "testPass", listener)

        verify { listener.onSyncFailed("Server not reachable. Check your internet connection.") }
    }
}
