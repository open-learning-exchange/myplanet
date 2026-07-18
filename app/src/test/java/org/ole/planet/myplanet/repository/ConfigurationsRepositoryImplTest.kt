package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigurationsRepositoryImplTest {

    private lateinit var repository: ConfigurationsRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    private val context: Context = mockk()
    private val apiInterface: ApiInterface = mockk()
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val appDatabase: AppDatabase = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val serviceScope = CoroutineScope(testDispatcher)

    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        repository = ConfigurationsRepositoryImpl(
            context,
            apiInterface,
            serviceScope,
            preferences,
            sharedPrefManager,
            appDatabase,
            serverUrlMapper,
            dispatcherProvider,
            TestTimeProvider()
        )
    }

    @Test
    fun `checkHealth calls listener with success message when server is accessible`() = runTest(testDispatcher) {
        val healthUrl = "http://test.url/healthaccess?p=1234"

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

        val result = repository.checkHealth()

        coVerify { apiInterface.healthAccess(healthUrl) }
        assertEquals("Success", result)
    }

    @Test
    fun `clearAllData delegates to Room clearAllTables`() = runTest(testDispatcher) {
        repository.clearAllData()

        io.mockk.verify(exactly = 1) { appDatabase.clearAllTables() }
    }
}
