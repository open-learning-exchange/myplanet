package org.ole.planet.myplanet.data

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utilities.IntentUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.LocaleUtils
import org.ole.planet.myplanet.utilities.NetworkUtils.extractProtocol
import org.ole.planet.myplanet.utilities.UrlUtils

class ConfigurationManager(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val retrofitInterface: ApiInterface?
) {

    fun getMinApk(
        listener: DataService.ConfigurationIdListener?,
        url: String,
        pin: String,
        activity: SyncActivity,
        callerActivity: String
    ) {
        Log.d("ServerSync", "=== getMinApk called ===")
        Log.d("ServerSync", "URL: $url")
        Log.d("ServerSync", "PIN provided: ${if (pin.isEmpty()) "NO" else "YES"}")
        Log.d("ServerSync", "Caller: $callerActivity")
        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(url)
        val urlsToTry = mutableListOf(url).apply { mapping.alternativeUrl?.let { add(it) } }
        Log.d("ServerSync", "URLs to try: ${urlsToTry.size} (${urlsToTry.joinToString(", ")})")

        MainApplication.applicationScope.launch {
            val customProgressDialog = withContext(Dispatchers.Main) {
                CustomProgressDialog(context).apply {
                    setText(context.getString(R.string.check_apk_version))
                    show()
                }
            }

            try {
                Log.d("ServerSync", "Starting URL checks in parallel...")
                val deferreds = urlsToTry.map { currentUrl ->
                    async { checkConfigurationUrl(currentUrl, pin, customProgressDialog) }
                }

                val result = try {
                    val allResults = deferreds.awaitAll()
                    Log.d("ServerSync", "All URL checks completed, processing results...")
                    allResults.firstOrNull { it is UrlCheckResult.Success }
                        ?: allResults.firstOrNull()
                        ?: UrlCheckResult.Failure(url)
                } catch (e: Exception) {
                    Log.e("ServerSync", "Exception during URL checks: ${e.message}", e)
                    e.printStackTrace()
                    UrlCheckResult.Failure(url)
                }

                when (result) {
                    is UrlCheckResult.Success -> {
                        Log.d("ServerSync", "URL check SUCCESS for: ${result.url}")
                        Log.d("ServerSync", "Configuration ID: ${result.id}, Code: ${result.code}")
                        val isAlternativeUrl = result.url != url
                        listener?.onConfigurationIdReceived(result.id, result.code, result.url, url, isAlternativeUrl, callerActivity)
                        activity.syncFailed = false
                    }
                    is UrlCheckResult.Failure -> {
                        Log.e("ServerSync", "URL check FAILED for: ${result.url}")
                        activity.syncFailed = true
                        val errorMessage = when (extractProtocol(url)) {
                            context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
                            context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
                            else -> context.getString(R.string.device_couldn_t_reach_local_server)
                        }
                        Log.e("ServerSync", "Showing error: $errorMessage")
                        showAlertDialog(errorMessage, false)
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerSync", "Outer exception in getMinApk: ${e.message}", e)
                e.printStackTrace()
                activity.syncFailed = true
                withContext(Dispatchers.Main) {
                    showAlertDialog(context.getString(R.string.device_couldn_t_reach_local_server), false)
                }
            } finally {
                customProgressDialog.dismiss()
                Log.d("ServerSync", "=== getMinApk finished ===")
            }
        }
    }

    private suspend fun checkConfigurationUrl(currentUrl: String, pin: String, customProgressDialog: CustomProgressDialog): UrlCheckResult {
        Log.d("ServerSync", "--- Checking configuration URL: $currentUrl")
        return try {
            val versionsUrl = "$currentUrl/versions"
            Log.d("ServerSync", "Attempting to fetch: $versionsUrl (timeout: 15s)")
            val versionsResponse = withTimeout(15_000) {
                retrofitInterface?.getConfiguration(versionsUrl)
            }

            if (versionsResponse == null) {
                Log.e("ServerSync", "versionsResponse is null for: $versionsUrl")
                return UrlCheckResult.Failure(currentUrl)
            }

            val code = versionsResponse.code()
            Log.d("ServerSync", "Response code from /versions: $code")

            if (versionsResponse.isSuccessful) {
                Log.d("ServerSync", "Successfully retrieved /versions")
                val jsonObject = versionsResponse.body()
                val minApkVersion = jsonObject?.get("minapk")?.asString
                val currentVersion = context.getString(R.string.app_version)
                Log.d("ServerSync", "MinApk version from server: $minApkVersion, Current app version: $currentVersion")

                if (minApkVersion != null && isVersionAllowed(currentVersion, minApkVersion)) {
                    Log.d("ServerSync", "Version check passed, building couchdb URL")
                    val couchdbURL = buildCouchdbUrl(currentUrl, pin)
                    val sanitized = couchdbURL.replace(Regex("://[^:]+:[^@]+@"), "://***:***@")
                    Log.d("ServerSync", "CouchDB URL: $sanitized")

                    withContext(Dispatchers.Main) {
                        customProgressDialog.setText(context.getString(R.string.checking_server))
                    }
                    Log.d("ServerSync", "Fetching configuration...")

                    fetchConfiguration(couchdbURL)?.let { (id, code) ->
                        Log.d("ServerSync", "Configuration fetched successfully: id=$id, code=$code")
                        return UrlCheckResult.Success(id, code, currentUrl)} ?: run {
                        Log.e("ServerSync", "fetchConfiguration returned null")
                    }
                } else {
                    Log.e("ServerSync", "Version check failed or minApk is null")
                }
            } else {
                Log.e("ServerSync", "/versions request failed with code: $code")
                val errorBody = versionsResponse.errorBody()?.string()
                if (!errorBody.isNullOrEmpty()) {
                    Log.e("ServerSync", "Error body: $errorBody")
                }
            }
            Log.e("ServerSync", "Returning failure for: $currentUrl")
            UrlCheckResult.Failure(currentUrl)
        } catch (e: TimeoutCancellationException) {
            Log.e("ServerSync", "Timeout (15s) trying to reach: $currentUrl/versions", e)
            e.printStackTrace()
            UrlCheckResult.Failure(currentUrl)
        } catch (e: Exception) {
            Log.e("ServerSync", "Exception checking $currentUrl: ${e.javaClass.simpleName}: ${e.message}", e)
            e.printStackTrace()
            UrlCheckResult.Failure(currentUrl)
        }
    }

    private suspend fun fetchConfiguration(couchdbURL: String): Pair<String, String>? {
        return try {
            val configUrl = "${getUrl(couchdbURL)}/configurations/_all_docs?include_docs=true"
            val sanitized = configUrl.replace(Regex("://[^:]+:[^@]+@"), "://***:***@")
            Log.d("ServerSync", "Fetching configuration from: $sanitized (timeout: 15s)")
            val configResponse = withTimeout(15_000) {
                retrofitInterface?.getConfiguration(configUrl)
            }

            if (configResponse == null) {
                Log.e("ServerSync", "configResponse is null")
                return null
            }

            val code = configResponse.code()
            Log.d("ServerSync", "Configuration response code: $code")

            if (configResponse.isSuccessful) {
                Log.d("ServerSync", "Configuration request successful")
                val rows = configResponse.body()?.getAsJsonArray("rows")
                Log.d("ServerSync", "Configuration rows count: ${rows?.size() ?: 0}")
                if (rows != null && rows.size() > 0) {
                    val firstRow = rows[0].asJsonObject
                    val id = firstRow.getAsJsonPrimitive("id").asString
                    val doc = firstRow.getAsJsonObject("doc")
                    val code = doc.getAsJsonPrimitive("code").asString
                    Log.d("ServerSync", "Configuration parsed: id=$id, code=$code")
                    processConfigurationDoc(doc)
                    return Pair(id, code)
                } else {
                    Log.e("ServerSync", "Configuration rows is null or empty")
                }
            } else {
                Log.e("ServerSync", "Configuration request failed with code: $code")
                val errorBody = configResponse.errorBody()?.string()
                if (!errorBody.isNullOrEmpty()) {
                    Log.e("ServerSync", "Configuration error body: $errorBody")
                }
            }
            null
        } catch (e: TimeoutCancellationException) {
            Log.e("ServerSync", "Timeout fetching configuration", e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            Log.e("ServerSync", "Exception fetching configuration: ${e.javaClass.simpleName}: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    private suspend fun processConfigurationDoc(doc: JsonObject) {
        val parentCode = doc.getAsJsonPrimitive("parentCode").asString

        withContext(Dispatchers.IO) {
            preferences.edit { putString("parentCode", parentCode) }
        }

        if (doc.has("preferredLang")) {
            val preferredLang = doc.getAsJsonPrimitive("preferredLang").asString
            val languageCode = getLanguageCodeFromName(preferredLang)
            if (languageCode != null) {
                withContext(Dispatchers.IO) {
                    LocaleUtils.setLocale(context, languageCode)
                    preferences.edit { putString("pendingLanguageChange", languageCode) }
                }
            }
        }

        if (doc.has("models")) {
            val modelsMap = doc.getAsJsonObject("models").entrySet()
                .associate { it.key to it.value.asString }

            withContext(Dispatchers.IO) {
                preferences.edit { putString("ai_models", JsonUtils.gson.toJson(modelsMap)) }
            }
        }

        if (doc.has("planetType")) {
            val planetType = doc.getAsJsonPrimitive("planetType").asString
            withContext(Dispatchers.IO) {
                preferences.edit { putString("planetType", planetType) }
            }
        }
    }

    private fun buildCouchdbUrl(currentUrl: String, pin: String): String {
        val uri = currentUrl.toUri()
        return if (currentUrl.contains("@")) {
            currentUrl
        } else {
            val urlUser = "satellite"
            "${uri.scheme}://$urlUser:$pin@${uri.host}:${if (uri.port == -1) if (uri.scheme == "http") 80 else 443 else uri.port}"
        }
    }

    sealed class UrlCheckResult {
        data class Success(val id: String, val code: String, val url: String) : UrlCheckResult()
        data class Failure(val url: String) : UrlCheckResult()
    }

    private fun isVersionAllowed(currentVersion: String, minApkVersion: String): Boolean {
        return compareVersions(currentVersion, minApkVersion) >= 0
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.removeSuffix("-lite").removePrefix("v").split(".").map { it.toInt() }
        val parts2 = version2.removePrefix("v").split(".").map { it.toInt() }

        for (i in 0 until kotlin.math.min(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
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

    fun showAlertDialog(message: String?, playStoreRedirect: Boolean) {
        MainApplication.applicationScope.launch(Dispatchers.Main) {
            val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            builder.setMessage(message)
            builder.setCancelable(true)
            builder.setNegativeButton(R.string.okay) { dialog: DialogInterface, _: Int ->
                if (playStoreRedirect) {
                    IntentUtils.openPlayStore(context)
                }
                dialog.cancel()
            }
            val alert = builder.create()
            alert.show()
        }
    }

    private fun getUrl(couchdbURL: String): String {
        return UrlUtils.dbUrl(couchdbURL)
    }
}
