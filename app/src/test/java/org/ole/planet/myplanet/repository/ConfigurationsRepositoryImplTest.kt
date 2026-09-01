package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Sha256Utils
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils
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

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
            TestTimeProvider(),
            Gson()
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

    @Test
    fun `checkServerAvailability with string url returns true when response has 8 or more items`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "1,2,3,4,5,6,7,8".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertTrue(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url returns false when response has less than 8 items`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "1,2,3".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url counts trailing commas as a single entry`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "1,2,3,4,5,6,7,8,,".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertTrue(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url returns false when response is only commas`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = ",,,".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url returns false when response body is empty`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url keeps internal empty entries when counting`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "a,,,b,,,,,,,h".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertTrue(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url returns false when response has exactly seven items`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "1,2,3,4,5,6,7".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url returns true when response is 401`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val mockBody = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(401, mockBody)

        coEvery { apiInterface.isPlanetAvailable(url) } returns response

        val result = repository.checkServerAvailability(url)

        assertTrue(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability with string url returns false when exception is thrown`() = runTest(testDispatcher) {
        val url = "http://test.url"

        coEvery { apiInterface.isPlanetAvailable(url) } throws Exception("Network error")

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
        coVerify { apiInterface.isPlanetAvailable(url) }
    }

    @Test
    fun `checkServerAvailability returns primary reachable result`() = runTest(testDispatcher) {
        val updateUrl = "http://test.url"
        every { sharedPrefManager.getServerUrl() } returns updateUrl

        val mapping = ServerUrlMapper.UrlMapping("http://primary.url", null)
        every { serverUrlMapper.processUrl(updateUrl) } returns mapping

        // Mock `checkServerAvailability` for primary
        val mockBody = "1,2,3,4,5,6,7,8".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)
        coEvery { apiInterface.isPlanetAvailable("http://primary.url") } returns response

        val result = repository.checkServerAvailability()

        assertTrue(result)
        verify { serverUrlMapper.processUrl(updateUrl) }
    }

    @Test
    fun `checkServerAvailability falls back to alternative url when primary fails`() = runTest(testDispatcher) {
        val updateUrl = "http://test.url"
        every { sharedPrefManager.getServerUrl() } returns updateUrl

        val mapping = ServerUrlMapper.UrlMapping("http://primary.url", "http://alt.url")
        every { serverUrlMapper.processUrl(updateUrl) } returns mapping

        // Mock `checkServerAvailability` for primary (fails)
        val failBody = "1".toResponseBody("text/plain".toMediaTypeOrNull())
        val failResponse = Response.success(200, failBody)
        coEvery { apiInterface.isPlanetAvailable("http://primary.url") } returns failResponse

        // Mock `checkServerAvailability` for alternative (succeeds)
        val successBody = "1,2,3,4,5,6,7,8".toResponseBody("text/plain".toMediaTypeOrNull())
        val successResponse = Response.success(200, successBody)
        coEvery { apiInterface.isPlanetAvailable("http://alt.url") } returns successResponse

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPrefManager.rawPreferences.edit() } returns editor
        every { serverUrlMapper.updateUrlPreferences(any(), any(), any(), any(), any()) } returns Unit

        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { android.net.Uri.parse(any()) } returns mockUri

        val result = repository.checkServerAvailability()

        assertTrue(result)
        verify { serverUrlMapper.processUrl(updateUrl) }
        verify { serverUrlMapper.updateUrlPreferences(editor, any(), "http://alt.url", "http://primary.url", any()) }
        io.mockk.unmockkStatic(android.net.Uri::class)
    }

    @Test
    fun `checkCheckSum returns true when checksums match`() = runTest(testDispatcher) {
        val path = "test_path"
        val checksumUrl = "http://test.url/checksum"
        val expectedChecksum = "matched_checksum"

        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        UrlUtils.init(sharedPrefManager)

        // Mock api response
        val mockBody = expectedChecksum.toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)
        coEvery { apiInterface.getChecksum(any()) } returns response

        io.mockk.mockkObject(FileUtils)
        val mockFile = mockk<java.io.File>()
        every { FileUtils.getSDPathFromUrl(context, path) } returns mockFile
        every { mockFile.exists() } returns true

        io.mockk.mockkConstructor(Sha256Utils::class)
        every { anyConstructed<Sha256Utils>().getCheckSumFromFile(mockFile) } returns expectedChecksum

        val result = repository.checkCheckSum(path)

        assertTrue(result)

        io.mockk.unmockkObject(FileUtils)
        io.mockk.unmockkConstructor(Sha256Utils::class)
    }

    @Test
    fun `checkCheckSum returns false when checksums do not match`() = runTest(testDispatcher) {
        val path = "test_path"
        val checksumUrl = "http://test.url/checksum"
        val expectedChecksum = "expected_checksum"
        val fileChecksum = "different_checksum"

        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        UrlUtils.init(sharedPrefManager)

        // Mock api response
        val mockBody = expectedChecksum.toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)
        coEvery { apiInterface.getChecksum(any()) } returns response

        io.mockk.mockkObject(FileUtils)
        val mockFile = mockk<java.io.File>()
        every { FileUtils.getSDPathFromUrl(context, path) } returns mockFile
        every { mockFile.exists() } returns true

        io.mockk.mockkConstructor(Sha256Utils::class)
        every { anyConstructed<Sha256Utils>().getCheckSumFromFile(mockFile) } returns fileChecksum

        val result = repository.checkCheckSum(path)

        assertFalse(result)

        io.mockk.unmockkObject(FileUtils)
        io.mockk.unmockkConstructor(Sha256Utils::class)
    }

    @Test
    fun `checkCheckSum returns false when checksum cannot be computed`() = runTest(testDispatcher) {
        val path = "test_path"
        val expectedChecksum = "expected_checksum"

        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        UrlUtils.init(sharedPrefManager)

        // Mock api response
        val mockBody = expectedChecksum.toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)
        coEvery { apiInterface.getChecksum(any()) } returns response

        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0

        io.mockk.mockkObject(FileUtils)
        val mockFile = mockk<java.io.File>()
        every { FileUtils.getSDPathFromUrl(context, path) } returns mockFile
        every { mockFile.exists() } returns true

        io.mockk.mockkConstructor(Sha256Utils::class)
        every { anyConstructed<Sha256Utils>().getCheckSumFromFile(mockFile) } returns null

        val result = repository.checkCheckSum(path)

        assertFalse(result)

        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkObject(FileUtils)
        io.mockk.unmockkConstructor(Sha256Utils::class)
    }

    @Test
    fun `checkCheckSum returns false when file does not exist`() = runTest(testDispatcher) {
        val path = "test_path"
        val checksumUrl = "http://test.url/checksum"
        val expectedChecksum = "matched_checksum"

        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        UrlUtils.init(sharedPrefManager)

        // Mock api response
        val mockBody = expectedChecksum.toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.success(200, mockBody)
        coEvery { apiInterface.getChecksum(any()) } returns response

        io.mockk.mockkObject(FileUtils)
        val mockFile = mockk<java.io.File>()
        every { FileUtils.getSDPathFromUrl(context, path) } returns mockFile
        every { mockFile.exists() } returns false

        val result = repository.checkCheckSum(path)

        assertFalse(result)

        io.mockk.unmockkObject(FileUtils)
    }

    @Test
    fun `checkCheckSum returns false on API error`() = runTest(testDispatcher) {
        val path = "test_path"

        every { sharedPrefManager.getServerUrl() } returns "http://test.url"
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://test.url"
        UrlUtils.init(sharedPrefManager)

        val mockBody = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(500, mockBody)
        coEvery { apiInterface.getChecksum(any()) } returns response

        val result = repository.checkCheckSum(path)

        assertFalse(result)
    }

    @Test
    fun `getMinApk returns Success when configuration is valid`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val pin = "1234"

        every { context.getString(R.string.app_version) } returns "1.0.0"

        val mapping = ServerUrlMapper.UrlMapping(url, null)
        every { serverUrlMapper.processUrl(url) } returns mapping

        // Mock checkConfigurationUrl response (versionsUrl)
        val versionsUrl = "$url/versions"
        val versionsJson = JsonObject().apply {
            add("minapk", JsonPrimitive("1.0.0"))
        }
        val versionsResponse = Response.success(200, versionsJson)

        // Mock fetchConfiguration response (configUrl)
        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { mockUri.scheme } returns "http"
        every { mockUri.host } returns "test.url"
        every { mockUri.port } returns 80
        every { android.net.Uri.parse(url) } returns mockUri

        val couchdbURL = "http://satellite:1234@test.url:80"
        val configUrl = "http://satellite:1234@test.url:80/configurations/_all_docs?include_docs=true"

        val docJson = JsonObject().apply {
            add("code", JsonPrimitive("config_code"))
            add("parentCode", JsonPrimitive("parent_code"))
        }
        val rowJson = JsonObject().apply {
            add("id", JsonPrimitive("config_id"))
            add("doc", docJson)
        }
        val rowsArray = JsonArray().apply { add(rowJson) }
        val configJson = JsonObject().apply {
            add("rows", rowsArray)
        }
        val configResponse = Response.success(200, configJson)

        coEvery { apiInterface.getConfiguration(versionsUrl) } returns versionsResponse
        coEvery { apiInterface.getConfiguration(configUrl) } returns configResponse

        io.mockk.mockkObject(VersionUtils)
        every { VersionUtils.isVersionAllowed(any(), any()) } returns true

        every { sharedPrefManager.setParentCode("parent_code") } returns Unit

        every { context.getString(R.string.http_protocol) } returns "http"
        every { context.getString(R.string.device_couldn_t_reach_local_server) } returns "Local server error"

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
        every { org.ole.planet.myplanet.utils.NetworkUtils.extractProtocol(url) } returns "http"

        io.mockk.mockkObject(UrlUtils)
        every { UrlUtils.dbUrl(any<String>()) } returns "http://satellite:1234@test.url:80"

        val result = repository.getMinApk(url, pin)

        assertTrue(result is ConfigurationsRepository.ConfigurationResult.Success)
        val successResult = result as ConfigurationsRepository.ConfigurationResult.Success
        assertEquals("config_id", successResult.id)
        assertEquals("config_code", successResult.code)
        assertEquals(url, successResult.url)
        assertEquals(url, successResult.defaultUrl)
        assertFalse(successResult.isAlternativeUrl)

        io.mockk.unmockkObject(UrlUtils)
        io.mockk.unmockkObject(VersionUtils)
        io.mockk.unmockkStatic(android.net.Uri::class)
        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
    }

    @Test
    fun `getMinApk returns Failure when version is not allowed`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val pin = "1234"

        every { context.getString(R.string.app_version) } returns "1.0.0"

        val mapping = ServerUrlMapper.UrlMapping(url, null)
        every { serverUrlMapper.processUrl(url) } returns mapping

        // Mock checkConfigurationUrl response (versionsUrl)
        val versionsUrl = "$url/versions"
        val versionsJson = JsonObject().apply {
            add("minapk", JsonPrimitive("2.0.0"))
        }
        val versionsResponse = Response.success(200, versionsJson)

        coEvery { apiInterface.getConfiguration(versionsUrl) } returns versionsResponse

        io.mockk.mockkObject(VersionUtils)
        every { VersionUtils.isVersionAllowed(any(), any()) } returns false

        every { context.getString(R.string.http_protocol) } returns "http"
        every { context.getString(R.string.device_couldn_t_reach_local_server) } returns "Local server error"

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
        every { org.ole.planet.myplanet.utils.NetworkUtils.extractProtocol(url) } returns "http"

        val result = repository.getMinApk(url, pin)

        assertTrue(result is ConfigurationsRepository.ConfigurationResult.Failure)
        val failureResult = result as ConfigurationsRepository.ConfigurationResult.Failure
        assertEquals("Local server error", failureResult.errorMessage)
        assertEquals(url, failureResult.url)

        io.mockk.unmockkObject(VersionUtils)
        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
    }

    @Test
    fun `getMinApk returns Failure on API error`() = runTest(testDispatcher) {
        val url = "http://test.url"
        val pin = "1234"

        val mapping = ServerUrlMapper.UrlMapping(url, null)
        every { serverUrlMapper.processUrl(url) } returns mapping

        val versionsUrl = "$url/versions"
        coEvery { apiInterface.getConfiguration(versionsUrl) } returns Response.error(500, "".toResponseBody("text/plain".toMediaTypeOrNull()))

        every { context.getString(R.string.http_protocol) } returns "http"
        every { context.getString(R.string.device_couldn_t_reach_local_server) } returns "Local server error"

        io.mockk.mockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
        every { org.ole.planet.myplanet.utils.NetworkUtils.extractProtocol(url) } returns "http"

        val result = repository.getMinApk(url, pin)

        assertTrue(result is ConfigurationsRepository.ConfigurationResult.Failure)

        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.NetworkUtils)
    }

    @Test
    fun `clearFirstRunStorageAndSetFlag wipes the ole directory and clears the flag`() = runTest(testDispatcher) {
        val oleDir = temporaryFolder.newFolder("ole")
        val looseFile = File(oleDir, "loose.txt").apply { writeText("stale") }
        val nested = File(oleDir, "nested").apply { mkdirs() }
        val nestedFile = File(nested, "deep.txt").apply { writeText("stale") }

        every { sharedPrefManager.getFirstRun() } returns true
        every { sharedPrefManager.setFirstRun(false) } just runs

        mockkObject(FileUtils)
        every { FileUtils.getOlePath(context) } returns oleDir.absolutePath

        try {
            repository.clearFirstRunStorageAndSetFlag(true)

            assertFalse(looseFile.exists())
            assertFalse(nestedFile.exists())
            assertFalse(nested.exists())
            assertTrue(oleDir.exists())
            verify { sharedPrefManager.setFirstRun(false) }
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `clearFirstRunStorageAndSetFlag does nothing without write permission`() = runTest(testDispatcher) {
        every { sharedPrefManager.getFirstRun() } returns true

        repository.clearFirstRunStorageAndSetFlag(false)

        verify(exactly = 0) { sharedPrefManager.setFirstRun(any()) }
    }

    @Test
    fun `clearFirstRunStorageAndSetFlag does nothing when it is not the first run`() = runTest(testDispatcher) {
        every { sharedPrefManager.getFirstRun() } returns false

        repository.clearFirstRunStorageAndSetFlag(true)

        verify(exactly = 0) { sharedPrefManager.setFirstRun(any()) }
    }

    @Test
    fun `getQueuedDownloads returns empty list when prefs is null`() = runTest(testDispatcher) {
        every { sharedPrefManager.getConcatenatedLinks() } returns null

        assertTrue(repository.getQueuedDownloads().isEmpty())
    }

    @Test
    fun `getQueuedDownloads returns empty list when prefs holds malformed JSON`() = runTest(testDispatcher) {
        every { sharedPrefManager.getConcatenatedLinks() } returns "not json"

        assertTrue(repository.getQueuedDownloads().isEmpty())
    }

    @Test
    fun `getQueuedDownloads returns decoded list when prefs has valid JSON`() = runTest(testDispatcher) {
        every { sharedPrefManager.getConcatenatedLinks() } returns "[\"link1\", \"link2\"]"

        assertEquals(listOf("link1", "link2"), repository.getQueuedDownloads())
    }

    @Test
    fun `getParentCode delegates to sharedPrefManager`() {
        every { sharedPrefManager.getParentCode() } returns "parent_123"

        assertEquals("parent_123", repository.getParentCode())
        verify { sharedPrefManager.getParentCode() }
    }

    @Test
    fun `getCommunityName delegates to sharedPrefManager`() {
        every { sharedPrefManager.getCommunityName() } returns "test_community"

        assertEquals("test_community", repository.getCommunityName())
        verify { sharedPrefManager.getCommunityName() }
    }

    @Test
    fun `getCommunityLeaders delegates to sharedPrefManager`() {
        every { sharedPrefManager.getCommunityLeaders() } returns "leaders_json"

        assertEquals("leaders_json", repository.getCommunityLeaders())
        verify { sharedPrefManager.getCommunityLeaders() }
    }

    @Test
    fun `clearPreferences delegates to sharedPrefManager`() {
        every { sharedPrefManager.clearPreferences() } just runs

        repository.clearPreferences()

        verify { sharedPrefManager.clearPreferences() }
    }
}
