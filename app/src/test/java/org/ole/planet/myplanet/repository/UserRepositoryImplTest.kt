package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

        var result: Pair<Boolean, String>? = null
        appScope.launch {
            result = repository.becomeMember(userObj)
        }
        advanceUntilIdle()

        assertFalse(result?.first ?: true)
        assertEquals(errorMessage, result?.second)
    }
}
