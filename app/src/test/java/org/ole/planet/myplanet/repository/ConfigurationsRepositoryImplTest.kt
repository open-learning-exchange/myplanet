package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@ExperimentalCoroutinesApi
class ConfigurationsRepositoryImplTest {

    private lateinit var repository: ConfigurationsRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context: Context = mockk()
    private val apiInterface: ApiInterface = mockk()
    private val preferences: SharedPreferences = mockk()
    private val sharedPrefManager: SharedPrefManager = mockk()
    private val databaseService: DatabaseService = mockk()
    private val serverUrlMapper: ServerUrlMapper = mockk()

    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    @Before
    fun setup() {
        repository = ConfigurationsRepositoryImpl(
            context,
            apiInterface,
            testScope,
            preferences,
            sharedPrefManager,
            databaseService,
            serverUrlMapper,
            dispatcherProvider
        )
    }

    @Test
    fun `checkHealth calls listener with success message when server is accessible`() = runTest(testDispatcher) {
        val listener: OnSuccessListener = mockk(relaxed = true)
        val healthUrl = "http://test.url/healthaccess?p=1234"

        // Mock preferences for UrlUtils
        val rawPrefs: SharedPreferences = mockk()
        every { sharedPrefManager.rawPreferences } returns rawPrefs
        every { rawPrefs.getString(any(), any()) } returns "http://test.url"
        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        every { sharedPrefManager.getServerPin() } returns "1234"

        val responseBody = "".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(200, responseBody)

        coEvery { apiInterface.healthAccess(any()) } returns response
        every { context.getString(R.string.server_sync_successfully) } returns "Success"

        repository.checkHealth(listener)

        advanceUntilIdle()

        coVerify { apiInterface.healthAccess(healthUrl) }
        verify { listener.onSuccess("Success") }
    }
}
