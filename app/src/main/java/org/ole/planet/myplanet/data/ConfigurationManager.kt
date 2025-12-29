package org.ole.planet.myplanet.data

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
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
        listener: Service.ConfigurationIdListener?,
        url: String,
        pin: String,
        activity: SyncActivity,
        callerActivity: String
    ) {
        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(url)
        val urlsToTry = mutableListOf(url).apply { mapping.alternativeUrl?.let { add(it) } }

        MainApplication.applicationScope.launch {
            val customProgressDialog = withContext(Dispatchers.Main) {
                CustomProgressDialog(context).apply {
                    setText(context.getString(R.string.check_apk_version))
                    show()
                }
            }

            try {
                val deferreds = urlsToTry.map { currentUrl ->
                    async { checkConfigurationUrl(currentUrl, pin, customProgressDialog) }
                }

                val result = try {
                    val allResults = deferreds.awaitAll()
                    allResults.firstOrNull { it is UrlCheckResult.Success }
                        ?: allResults.firstOrNull()
                        ?: UrlCheckResult.Failure(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    UrlCheckResult.Failure(url)
                }

                when (result) {
                    is UrlCheckResult.Success -> {
                        val isAlternativeUrl = result.url != url
                        listener?.onConfigurationIdReceived(result.id, result.code, result.url, url, isAlternativeUrl, callerActivity)
                        activity.syncFailed = false
                    }
                    is UrlCheckResult.Failure -> {
                        activity.syncFailed = true
                        val errorMessage = when (extractProtocol(url)) {
                            context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
                            context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
                            else -> context.getString(R.string.device_couldn_t_reach_local_server)
                        }
                        showAlertDialog(errorMessage, false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity.syncFailed = true
                withContext(Dispatchers.Main) {
                    showAlertDialog(context.getString(R.string.device_couldn_t_reach_local_server), false)
                }
            } finally {
                customProgressDialog.dismiss()
            }
        }
    }

    private suspend fun checkConfigurationUrl(currentUrl: String, pin: String, customProgressDialog: CustomProgressDialog): UrlCheckResult {
        return try {
            val versionsResponse = withTimeout(15_000) {
                retrofitInterface?.getConfiguration("$currentUrl/versions")
            }

            if (versionsResponse?.isSuccessful == true) {
                val jsonObject = versionsResponse.body()
                val minApkVersion = jsonObject?.get("minapk")?.asString
                val currentVersion = context.getString(R.string.app_version)

                if (minApkVersion != null && isVersionAllowed(currentVersion, minApkVersion)) {
                    val couchdbURL = buildCouchdbUrl(currentUrl, pin)

                    withContext(Dispatchers.Main) {
                        customProgressDialog.setText(context.getString(R.string.checking_server))
                    }

                    fetchConfiguration(couchdbURL)?.let { (id, code) ->
                        return UrlCheckResult.Success(id, code, currentUrl)
                    }
                }
            }
            UrlCheckResult.Failure(currentUrl)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            UrlCheckResult.Failure(currentUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            UrlCheckResult.Failure(currentUrl)
        }
    }

    private suspend fun fetchConfiguration(couchdbURL: String): Pair<String, String>? {
        return try {
            val configResponse = withTimeout(15_000) {
                retrofitInterface?.getConfiguration("${getUrl(couchdbURL)}/configurations/_all_docs?include_docs=true")
            }

            if (configResponse?.isSuccessful == true) {
                val rows = configResponse.body()?.getAsJsonArray("rows")
                if (rows != null && rows.size() > 0) {
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
            e.printStackTrace()
            null
        } catch (e: Exception) {
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
