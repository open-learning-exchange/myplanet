package org.ole.planet.myplanet.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.PlainGson
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LocaleUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.Sha256Utils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils

class ConfigurationsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiInterface: ApiInterface,
    @param:ApplicationScope private val serviceScope: CoroutineScope,
    private val sharedPrefManager: SharedPrefManager,
    private val appDatabase: AppDatabase,
    private val serverUrlMapper: ServerUrlMapper,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
    @PlainGson private val gson: Gson
) : ConfigurationsRepository {
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    companion object {
        private const val TAG = "ConfigurationsRepository"
    }

    override suspend fun checkHealth(): String {
        return try {
            val healthUrl = UrlUtils.getHealthAccessUrl(sharedPrefManager)
            if (healthUrl.isBlank()) {
                return ""
            }

            try {
                val response = apiInterface.healthAccess(healthUrl)
                when (response.code()) {
                    200 -> context.getString(R.string.server_sync_successfully)
                    401 -> "Unauthorized - Invalid credentials"
                    404 -> "Server endpoint not found"
                    500 -> "Server internal error"
                    502 -> "Bad gateway - Server unavailable"
                    503 -> "Service temporarily unavailable"
                    504 -> "Gateway timeout"
                    else -> "Server error: ${response.code()}"
                }
            } catch (t: Exception) {
                Log.e(TAG, "Health access request failed", t)
                when (t) {
                    is UnknownHostException -> "Server not reachable"
                    is SocketTimeoutException -> "Connection timeout"
                    is ConnectException -> "Unable to connect to server"
                    is IOException -> "Network connection error"
                    else -> "Network error: ${t.localizedMessage ?: "Unknown error"}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health access initialization failed", e)
            "Health access initialization failed"
        }
    }

    override fun checkVersion(callback: ConfigurationsRepository.CheckVersionCallback, spm: SharedPrefManager) {
        val baseUrl = UrlUtils.baseUrl(spm)
        if (baseUrl.isEmpty()) {
            callback.onError(context.getString(R.string.server_url_not_configured), true)
            return
        }

        serviceScope.launch(dispatcherProvider.io) {
            callback.onCheckingVersion()

            val lastCheckTime = sharedPrefManager.rawPreferences.getLong("last_version_check_timestamp", 0)
            val currentTime = timeProvider.now()
            val twentyFourHoursInMillis = 24 * 60 * 60 * 1000

            if (currentTime - lastCheckTime < twentyFourHoursInMillis) {
                val cachedVersionDetail = sharedPrefManager.getVersionDetail()
                val cachedApkVersion = sharedPrefManager.rawPreferences.getInt("cachedApkVersion", -1)

                if (cachedVersionDetail != null && cachedApkVersion != -1) {
                    try {
                        val cachedInfo = gson.fromJson(cachedVersionDetail, MyPlanet::class.java)
                        handleVersionEvaluation(cachedInfo, cachedApkVersion, callback)
                        return@launch
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse cached version detail", e)
                    }
                }
            }

            try {
                val planetInfo = fetchVersionInfo(spm)
                if (planetInfo == null) {
                    callback.onError(context.getString(R.string.version_not_found), true)
                    return@launch
                }

                sharedPrefManager.rawPreferences.edit {
                    putLong("last_version_check_timestamp", timeProvider.now())
                }
                sharedPrefManager.setLastWifiId(NetworkUtils.getCurrentNetworkId(context))
                sharedPrefManager.setVersionDetail(gson.toJson(planetInfo))

                val rawApkVersion = fetchApkVersionString(spm)
                val versionStr = gson.fromJson(rawApkVersion, String::class.java)
                if (versionStr.isNullOrEmpty()) {
                    callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                    return@launch
                }

                val apkVersion = VersionUtils.parseApkVersionString(versionStr)
                    ?: run {
                        callback.onError(
                            context.getString(R.string.new_apk_version_required_but_not_found_on_server),
                            false
                        )
                        return@launch
                    }

                sharedPrefManager.rawPreferences.edit {
                    putInt("cachedApkVersion", apkVersion)
                }

                handleVersionEvaluation(planetInfo, apkVersion, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Version check failed", e)
                withContext(dispatcherProvider.main) {
                    callback.onError(context.getString(R.string.connection_failed), true)
                }
            }
        }
    }

    override suspend fun checkServerAvailability(): Boolean {
        val updateUrl = sharedPrefManager.getServerUrl()
        serverAvailabilityCache[updateUrl]?.let { (available, timestamp) ->
            if (timeProvider.now() - timestamp < 30000) {
                return available
            }
        }

        val mapping = serverUrlMapper.processUrl(updateUrl)

        val result = withContext(dispatcherProvider.io) {
            val primaryReachable = checkServerAvailability(mapping.primaryUrl)
            val alternativeReachable = mapping.alternativeUrl?.let {
                checkServerAvailability(it)
            } == true

            if (!primaryReachable && alternativeReachable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = updateUrl.toUri()
                    val editor = sharedPrefManager.rawPreferences.edit()

                    serverUrlMapper.updateUrlPreferences(
                        editor,
                        uri,
                        alternativeUrl,
                        mapping.primaryUrl,
                        sharedPrefManager.rawPreferences
                    )
                }
                alternativeReachable
            } else {
                primaryReachable
            }
        }

        serverAvailabilityCache[updateUrl] = Pair(result, timeProvider.now())
        return result
    }

    override suspend fun clearAllData() {
        withContext(dispatcherProvider.io) {
            appDatabase.clearAllTables()
        }
    }

    override suspend fun checkServerAvailability(url: String): Boolean {
        return try {
            val response = apiInterface.isPlanetAvailable(url)
            val code = response.code()
            if (response.isSuccessful) {
                val ss = withContext(dispatcherProvider.io) { response.body()?.string() }
                val dbCount = countCommaEntries(ss)
                dbCount >= 8
            } else {
                code == 401
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun countCommaEntries(body: String?): Int {
        if (body == null) return 0
        var end = body.length
        while (end > 0 && body[end - 1] == ',') end--
        if (end == 0) return 0
        var commaCount = 0
        for (i in 0 until end) {
            if (body[i] == ',') commaCount++
        }
        return commaCount + 1
    }

    override suspend fun checkCheckSum(path: String): Boolean {
        return try {
            val response = apiInterface.getChecksum(UrlUtils.getChecksumUrl(sharedPrefManager))
            if (response.isSuccessful) {
                val checksum = withContext(dispatcherProvider.io) { response.body()?.string() }
                if (!checksum.isNullOrEmpty()) {
                    val f = FileUtils.getSDPathFromUrl(context, path)
                    if (f.exists()) {
                        val sha256 = withContext(dispatcherProvider.io) {
                            Sha256Utils().getCheckSumFromFile(f)
                        }
                        if (sha256 == null) {
                            Log.w(TAG, "Could not compute checksum for $path")
                            return false
                        }
                        return checksum.contains(sha256)
                    }
                }
            }
            false
        } catch (e: IOException) {
            Log.e(TAG, "Checksum check failed", e)
            false
        }
    }

    override suspend fun getMinApk(url: String, pin: String): ConfigurationsRepository.ConfigurationResult {
        val mapping = serverUrlMapper.processUrl(url)
        val urlsToTry = mutableListOf(url).apply { mapping.alternativeUrl?.let { add(it) } }

        return try {
            val deferreds = urlsToTry.map { currentUrl ->
                serviceScope.async { checkConfigurationUrl(currentUrl, pin) }
            }

            val result = try {
                val allResults = deferreds.awaitAll()
                allResults.firstOrNull { it is UrlCheckResult.Success }
                    ?: allResults.firstOrNull()
                    ?: UrlCheckResult.Failure(url)
            } catch (e: Exception) {
                Log.e(TAG, "Configuration URL check failed", e)
                UrlCheckResult.Failure(url)
            }

            when (result) {
                is UrlCheckResult.Success -> {
                    val isAlternativeUrl = result.url != url
                    ConfigurationsRepository.ConfigurationResult.Success(result.id, result.code, result.url, url, isAlternativeUrl)
                }
                is UrlCheckResult.Failure -> {
                    val errorMessage = when (NetworkUtils.extractProtocol(url)) {
                        context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
                        context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
                        else -> context.getString(R.string.device_couldn_t_reach_local_server)
                    }
                    ConfigurationsRepository.ConfigurationResult.Failure(errorMessage, url)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMinApk failed", e)
            ConfigurationsRepository.ConfigurationResult.Failure(context.getString(R.string.device_couldn_t_reach_local_server), url)
        }
    }

    private suspend fun checkConfigurationUrl(currentUrl: String, pin: String): UrlCheckResult {
        return try {
            val versionsUrl = "$currentUrl/versions"

            val versionsResponse = withTimeout(15_000) {
                apiInterface.getConfiguration(versionsUrl)
            }

            if (versionsResponse.isSuccessful) {
                val jsonObject = versionsResponse.body()
                val minApkVersion = jsonObject?.get("minapk")?.asString
                val currentVersion = context.getString(R.string.app_version)

                if (minApkVersion != null && VersionUtils.isVersionAllowed(currentVersion, minApkVersion)) {
                    val couchdbURL = buildCouchdbUrl(currentUrl, pin)

                    fetchConfiguration(couchdbURL)?.let { (id, code) ->
                        return UrlCheckResult.Success(id, code, currentUrl)
                    } ?: run {}
                }
            }
            UrlCheckResult.Failure(currentUrl)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Configuration URL check timed out", e)
            UrlCheckResult.Failure(currentUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Configuration URL check failed", e)
            UrlCheckResult.Failure(currentUrl)
        }
    }

    private suspend fun fetchConfiguration(couchdbURL: String): Pair<String, String>? {
        return try {
            val configUrl = "${getUrl(couchdbURL)}/configurations/_all_docs?include_docs=true"
            val configResponse = withTimeout(15_000) {
                apiInterface.getConfiguration(configUrl)
            }

            if (configResponse.isSuccessful) {
                val rows = configResponse.body()?.getAsJsonArray("rows")

                if (rows != null && !rows.isEmpty()) {
                    val firstRow = rows[0].asJsonObject
                    val id = firstRow.getAsJsonPrimitive("id").asString
                    val doc = firstRow.getAsJsonObject("doc")
                    val code = doc.getAsJsonPrimitive("code").asString
                    processConfigurationDoc(doc)
                    return Pair(id, code)
                }
            }
            null
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Fetch configuration timed out", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fetch configuration failed", e)
            null
        }
    }

    private suspend fun processConfigurationDoc(doc: JsonObject) = withContext(dispatcherProvider.io) {
        val parentCode = doc.getAsJsonPrimitive("parentCode").asString
        sharedPrefManager.setParentCode(parentCode)

        if (doc.has("preferredLang")) {
            val preferredLang = doc.getAsJsonPrimitive("preferredLang").asString
            val languageCode = getLanguageCodeFromName(preferredLang)
            if (languageCode != null) {
                LocaleUtils.setLocale(context, languageCode)
                sharedPrefManager.setPendingLanguageChange(languageCode)
            }
        }

        if (doc.has("models")) {
            val modelsMap = doc.getAsJsonObject("models").entrySet()
                .associate { it.key to it.value.asString }
            sharedPrefManager.rawPreferences.edit { putString("ai_models", gson.toJson(modelsMap)) }
        }

        if (doc.has("planetType")) {
            val planetType = doc.getAsJsonPrimitive("planetType").asString
            sharedPrefManager.rawPreferences.edit { putString("planetType", planetType) }
        }
    }

    override fun getPlanetType(): String? {
        return sharedPrefManager.getRawString("planetType")
    }

    override fun getParentCode(): String {
        return sharedPrefManager.getParentCode()
    }

    override fun getCommunityName(): String {
        return sharedPrefManager.getCommunityName()
    }

    override fun getCommunityLeaders(): List<UserEntity> {
        return UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders())
    }

    override fun clearPreferences() {
        sharedPrefManager.clearPreferences()
    }

    override suspend fun clearFirstRunStorageAndSetFlag(hasWritePermission: Boolean) {
        withContext(dispatcherProvider.io) {
            if (hasWritePermission && sharedPrefManager.getFirstRun()) {
                val myDir = File(FileUtils.getOlePath(context))
                if (myDir.isDirectory) {
                    myDir.listFiles()?.forEach { it.deleteRecursively() }
                }
                sharedPrefManager.setFirstRun(false)
            }
        }
    }

    override suspend fun getQueuedDownloads(): List<String> {
        return withContext(dispatcherProvider.io) {
            val storedJsonConcatenatedLinks = sharedPrefManager.getConcatenatedLinks()
            if (storedJsonConcatenatedLinks.isNullOrEmpty()) {
                emptyList()
            } else {
                runCatching {
                    Json.decodeFromString<List<String>>(storedJsonConcatenatedLinks)
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun buildCouchdbUrl(currentUrl: String, pin: String): String {
        val uri = currentUrl.toUri()
        return if (currentUrl.contains("@")) {
            currentUrl
        } else {
            val urlUser = "satellite"
            val scheme = uri.scheme
            val host = uri.host
            val port = if (uri.port == -1) {
                if (scheme == "http") 80 else 443
            } else {
                uri.port
            }
            "$scheme://$urlUser:$pin@$host:$port"
        }
    }

    private fun getLanguageCodeFromName(languageName: String): String? {
        return when (languageName.lowercase()) {
            "english" -> "en"
            "spanish", "español" -> "es"
            "somali" -> "so"
            "nepali" -> "ne"
            "arabic", "العربية" -> "ar"
            "french", "français" -> "fr"
            else -> null
        }
    }

    private fun getUrl(couchdbURL: String): String {
        return UrlUtils.dbUrl(couchdbURL)
    }

    private sealed class UrlCheckResult {
        data class Success(val id: String, val code: String, val url: String) : UrlCheckResult()
        data class Failure(val url: String) : UrlCheckResult()
    }

    private suspend fun fetchVersionInfo(spm: SharedPrefManager): MyPlanet? =
        withContext(dispatcherProvider.io) {
            val result = ApiClient.executeWithResult {
                apiInterface.checkVersion(UrlUtils.getUpdateUrl(spm))
            }
            when (result) {
                is NetworkResult.Success -> result.data
                else -> null
            }
        }

    private suspend fun fetchApkVersionString(spm: SharedPrefManager): String? =
        withContext(dispatcherProvider.io) {
            val result = ApiClient.executeWithResult {
                apiInterface.getApkVersion(UrlUtils.getApkVersionUrl(spm))
            }
            when (result) {
                is NetworkResult.Success -> result.data.string()
                else -> null
            }
        }

    private fun handleVersionEvaluation(info: MyPlanet, apkVersion: Int, callback: ConfigurationsRepository.CheckVersionCallback) {
        val currentVersion = VersionUtils.getVersionCode(context)
        if (Constants.showBetaFeature(Constants.KEY_UPGRADE_MAX, context) && info.latestapkcode > currentVersion) {
            callback.onUpdateAvailable(info, false)
        } else if (apkVersion > currentVersion) {
            callback.onUpdateAvailable(info, currentVersion >= info.minapkcode)
        } else if (currentVersion < info.minapkcode && apkVersion < info.minapkcode) {
            callback.onUpdateAvailable(info, true)
        } else {
            callback.onError(context.getString(R.string.planet_is_up_to_date), false)
        }
    }

    override suspend fun ensureServerUrlUpdated() {
        val serverUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(serverUrl)
        if (mapping.alternativeUrl != null) {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                serverUrlMapper.isUrlDirectlyReachable(url)
            }
        }
    }
}
