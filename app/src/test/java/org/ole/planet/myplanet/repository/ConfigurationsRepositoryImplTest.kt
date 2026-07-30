package org.ole.planet.myplanet.repository

import io.mockk.verify
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import android.content.Context
import android.content.SharedPreferences
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
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
        override val mainImmediate = testDispatcher
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

    @Test
    fun `checkVersion calls onError if baseUrl is empty`() = runTest(testDispatcher) {
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns ""
        every { context.getString(R.string.server_url_not_configured) } returns "Server URL not configured"

        UrlUtils.init(sharedPrefManager)

        val callback = mockk<ConfigurationsRepository.CheckVersionCallback>(relaxed = true)

        repository.checkVersion(callback, sharedPrefManager)

        verify { callback.onError("Server URL not configured", true) }
    }

    @Test
    fun `checkVersion with valid url checks cached version if within 24 hours`() = runTest(testDispatcher) {
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"

        val rawPrefs: SharedPreferences = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns rawPrefs

        // Return 0 for last check, and we will keep timeProvider.now() at 0
        every { rawPrefs.getLong("last_version_check_timestamp", 0) } returns 0L

        val myPlanet = MyPlanet().apply {
            planetVersion = "v1.0"
            minapkcode = 1
            latestapkcode = 2
        }
        val planetJson = JsonUtils.gson.toJson(myPlanet)

        every { sharedPrefManager.getVersionDetail() } returns planetJson
        every { rawPrefs.getInt("cachedApkVersion", -1) } returns 2

        every { context.packageName } returns "org.ole.planet.myplanet"

        // Mock getVersionCode from context
        val pm = mockk<android.content.pm.PackageManager>()
        val packageInfo = android.content.pm.PackageInfo().apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                longVersionCode = 1L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 1
            }
        }
        every { context.packageManager } returns pm
        every { pm.getPackageInfo("org.ole.planet.myplanet", 0) } returns packageInfo

        every { context.getString(R.string.planet_is_up_to_date) } returns "Planet is up to date"

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.Constants)
        every { org.ole.planet.myplanet.utils.Constants.showBetaFeature(any(), any()) } returns false

        UrlUtils.init(sharedPrefManager)

        val callback = mockk<ConfigurationsRepository.CheckVersionCallback>(relaxed = true)

        repository.checkVersion(callback, sharedPrefManager)

        // advance coroutine time for serviceScope
        testDispatcher.scheduler.advanceUntilIdle()

        verify { callback.onCheckingVersion() }
        verify(exactly = 1) { callback.onUpdateAvailable(any(), any()) }

        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.Constants)
    }

    @Test
    fun `checkVersion fetches version info if cache is older than 24 hours`() = runTest(testDispatcher) {
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.getUrlUser() } returns "user"
        every { sharedPrefManager.getUrlPwd() } returns "pwd"
        every { sharedPrefManager.getUrlScheme() } returns "http"
        every { sharedPrefManager.getUrlHost() } returns "test.url"
        every { sharedPrefManager.rawPreferences.getInt("url_port", 80) } returns 80

        val rawPrefs: SharedPreferences = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns rawPrefs

        // Return a time older than 24 hours (24 * 60 * 60 * 1000 = 86400000)
        every { rawPrefs.getLong("last_version_check_timestamp", 0) } returns -86400001L

        val myPlanet = MyPlanet().apply {
            planetVersion = "v1.0"
            minapkcode = 1
            latestapkcode = 2
        }

        val responsePlanet = retrofit2.Response.success(myPlanet)
        val apkStringJson = JsonUtils.gson.toJson("v3")
        val responseApk = retrofit2.Response.success(apkStringJson.toResponseBody("application/json".toMediaTypeOrNull()))

        coEvery { apiInterface.checkVersion(any()) } returns responsePlanet
        coEvery { apiInterface.getApkVersion(any()) } returns responseApk

        every { context.getString(R.string.planet_is_up_to_date) } returns "Planet is up to date"
        every { context.packageName } returns "org.ole.planet.myplanet"

        // Mock getVersionCode from context
        val pm = mockk<android.content.pm.PackageManager>()
        val packageInfo = android.content.pm.PackageInfo().apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                longVersionCode = 1L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 1
            }
        }
        every { context.packageManager } returns pm
        every { pm.getPackageInfo("org.ole.planet.myplanet", 0) } returns packageInfo

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.Constants)
        every { org.ole.planet.myplanet.utils.Constants.showBetaFeature(any(), any()) } returns false

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
        every { org.ole.planet.myplanet.utils.NetworkUtils.getCurrentNetworkId(context) } returns 1

        UrlUtils.init(sharedPrefManager)

        val callback = mockk<ConfigurationsRepository.CheckVersionCallback>(relaxed = true)

        repository.checkVersion(callback, sharedPrefManager)

        testDispatcher.scheduler.advanceUntilIdle()

        verify { callback.onCheckingVersion() }
        coVerify(exactly = 1) { apiInterface.checkVersion(any()) }
        coVerify(exactly = 1) { apiInterface.getApkVersion(any()) }
        verify(exactly = 1) { callback.onUpdateAvailable(any(), any()) }

        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.Constants)
        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
    }
}
