package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var settings: SharedPreferences
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var apiInterface: ApiInterface
    private lateinit var uploadToShelfService: Lazy<UploadToShelfService>
    private lateinit var context: Context
    private lateinit var configurationsRepository: ConfigurationsRepository
    private lateinit var appScope: CoroutineScope
    private lateinit var dispatcherProvider: DispatcherProvider

    private lateinit var repository: UserRepositoryImpl

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "Basic auth"
        every { UrlUtils.getUrl() } returns "http://test.url"

        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        databaseService = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        uploadToShelfService = mockk(relaxed = true)
        context = mockk(relaxed = true)
        configurationsRepository = mockk(relaxed = true)
        appScope = TestScope(testDispatcher)

        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        repository = UserRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            settings,
            sharedPrefManager,
            apiInterface,
            uploadToShelfService,
            context,
            configurationsRepository,
            appScope,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
        unmockkStatic(Log::class)
    }

    @Test
    fun `becomeMember uses dispatcherProvider IO`() = runTest(testDispatcher) {
        val userName = "testUser"
        val userObj = JsonObject().apply { addProperty("name", userName) }
        val userUrl = "http://test.url/_users/org.couchdb.user:$userName"
        val errorMessage = "User already exists"

        coEvery { configurationsRepository.checkServerAvailability() } returns true
        every { context.getString(R.string.unable_to_create_user_user_already_exists) } returns errorMessage

        // Mock API response to simulate user already exists
        val existsResponseBody = JsonObject().apply { addProperty("_id", "some_id") }
        val response = Response.success(existsResponseBody)
        coEvery { apiInterface.getJsonObject("Basic auth", userUrl) } returns response

        val result = repository.becomeMember(userObj)
        advanceUntilIdle()

        assertFalse(result.first)
        assertEquals(errorMessage, result.second)
    }

    @Test
    fun `becomeMember succeeds on happy path`() = runTest(testDispatcher) {
        val userName = "newUser"
        val userObj = JsonObject().apply { addProperty("name", userName) }
        val userUrl = "http://test.url/_users/org.couchdb.user:$userName"
        val successMessage = "User created successfully"
        val id = "new_user_id"

        coEvery { configurationsRepository.checkServerAvailability() } returns true
        every { context.getString(R.string.user_created_successfully) } returns successMessage

        // 1. User doesn't exist check
        val notExistsResponseBody = JsonObject()
        val notFoundResponse = Response.success(notExistsResponseBody)
        coEvery { apiInterface.getJsonObject("Basic auth", userUrl) } returns notFoundResponse

        // 2. User creation mock
        val createdResponseBody = JsonObject().apply { addProperty("id", id) }
        val createdResponse = Response.success(createdResponseBody)
        coEvery { apiInterface.putDoc(null, "application/json", userUrl, userObj) } returns createdResponse

        // 3. User save to db fetch
        val userFetchUrl = "http://test.url/_users/$id"
        val userFetchResponse = Response.success(JsonObject().apply {
            addProperty("_id", id)
            addProperty("name", userName)
        })
        coEvery { apiInterface.getJsonObject("Basic auth", userFetchUrl) } returns userFetchResponse

        // Stub saveUser to return a mocked RealmUser instead of attempting DB operations
        val spyRepository = spyk(repository)
        val mockRealmUser = mockk<org.ole.planet.myplanet.model.RealmUser>(relaxed = true)
        coEvery { spyRepository.saveUser(any(), any(), any(), any()) } returns mockRealmUser

        val result = spyRepository.becomeMember(userObj)
        advanceUntilIdle()

        assertEquals(true, result.first)
        assertEquals(successMessage, result.second)
    }
}
