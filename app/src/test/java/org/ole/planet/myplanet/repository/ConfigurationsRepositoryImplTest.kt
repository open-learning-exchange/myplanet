package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
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
import org.junit.After
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyPlanet
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


    @After
    fun teardown() {
        unmockkAll()
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

        coVerify(exactly = 1) { appDatabase.clearAllTables() }
    }

    @Test
    fun `checkVersion caches network call within 24 hours`() = runTest(testDispatcher) {
        val rawPrefs: SharedPreferences = mockk(relaxed = true)
        val spmEditor: SharedPreferences.Editor = mockk(relaxed = true)

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        every { org.ole.planet.myplanet.utils.UrlUtils.baseUrl(any()) } returns "http://test.url"

        every { sharedPrefManager.rawPreferences } returns rawPrefs
        every { rawPrefs.edit() } returns spmEditor
        every { spmEditor.putLong(any(), any()) } returns spmEditor

        val timeProvider = TestTimeProvider()
        every { rawPrefs.getLong("last_version_check_timestamp", 0) } returns timeProvider.now()

        val cachedMyPlanet = org.ole.planet.myplanet.model.MyPlanet().apply {
            latestapkcode = 10
            minapkcode = 5
        }
        val cachedJson = org.ole.planet.myplanet.utils.JsonUtils.gson.toJson(cachedMyPlanet)
        every { sharedPrefManager.getVersionDetail() } returns cachedJson
        every { rawPrefs.getInt("cachedApkVersion", -1) } returns 10

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.VersionUtils)
        every { org.ole.planet.myplanet.utils.VersionUtils.getVersionCode(any()) } returns 5
        io.mockk.mockkObject(org.ole.planet.myplanet.utils.Constants)
        every { org.ole.planet.myplanet.utils.Constants.showBetaFeature(any(), any()) } returns false

        val callback = mockk<ConfigurationsRepository.CheckVersionCallback>(relaxed = true)

        repository = ConfigurationsRepositoryImpl(
            context, apiInterface, serviceScope, sharedPrefManager, appDatabase, serverUrlMapper, dispatcherProvider, timeProvider
        )

        repository.checkVersion(callback, sharedPrefManager)
        delay(100)

        io.mockk.verify(timeout = 1000) { callback.onCheckingVersion() }
        io.mockk.verify(timeout = 1000) { callback.onUpdateAvailable(any(), true) }

        coVerify(exactly = 0) { apiInterface.checkVersion(any()) }
        coVerify(exactly = 0) { apiInterface.getApkVersion(any()) }

    }

    @Test
    fun `checkVersion fetches from network after 24 hours`() = runTest(testDispatcher) {
        val rawPrefs: SharedPreferences = mockk(relaxed = true)
        val spmEditor: SharedPreferences.Editor = mockk(relaxed = true)

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        every { org.ole.planet.myplanet.utils.UrlUtils.baseUrl(any()) } returns "http://test.url"
        every { org.ole.planet.myplanet.utils.UrlUtils.getUpdateUrl(any()) } returns "http://test.url/update"
        every { org.ole.planet.myplanet.utils.UrlUtils.getApkVersionUrl(any()) } returns "http://test.url/apk"

        every { sharedPrefManager.rawPreferences } returns rawPrefs
        every { rawPrefs.edit() } returns spmEditor
        every { spmEditor.putLong(any(), any()) } returns spmEditor

        val timeProvider = TestTimeProvider()
        every { rawPrefs.getLong("last_version_check_timestamp", 0) } returns (timeProvider.now() - 25L * 60 * 60 * 1000)

        val fetchedPlanet = org.ole.planet.myplanet.model.MyPlanet().apply {
            latestapkcode = 12
            minapkcode = 6
        }

        val versionResponse = Response.success(fetchedPlanet)
        coEvery { apiInterface.checkVersion(any()) } returns versionResponse

        val bodyString = "\"12\""
        val apkVersionResponse: retrofit2.Response<okhttp3.ResponseBody> = retrofit2.Response.success(bodyString.toResponseBody(null))
        coEvery { apiInterface.getApkVersion(any()) } returns apkVersionResponse

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.VersionUtils)
        every { org.ole.planet.myplanet.utils.VersionUtils.parseApkVersionString(any()) } returns 12
        every { org.ole.planet.myplanet.utils.VersionUtils.getVersionCode(any()) } returns 5

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.Constants)
        every { org.ole.planet.myplanet.utils.Constants.showBetaFeature(any(), any()) } returns false
        io.mockk.mockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
        every { org.ole.planet.myplanet.utils.NetworkUtils.getCurrentNetworkId(any()) } returns 1

        val callback = mockk<ConfigurationsRepository.CheckVersionCallback>(relaxed = true)

        repository = ConfigurationsRepositoryImpl(
            context, apiInterface, serviceScope, sharedPrefManager, appDatabase, serverUrlMapper, dispatcherProvider, timeProvider
        )

        repository.checkVersion(callback, sharedPrefManager)
        delay(100)

        coVerify(timeout = 1000, exactly = 1) { apiInterface.checkVersion(any()) }
        coVerify(timeout = 1000, exactly = 1) { apiInterface.getApkVersion(any()) }

        io.mockk.verify(timeout = 1000) { callback.onUpdateAvailable(fetchedPlanet, false) }

    }

}
