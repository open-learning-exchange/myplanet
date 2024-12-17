package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity

class URLProcessor(private val context: Context, private val lifecycleScope: CoroutineScope, private val settings: SharedPreferences, private val editor: SharedPreferences.Editor, private val mRealm: Realm, private val profileDbHandler: UserProfileDbHandler) {
    companion object {
        private const val TAG = "URLProcessor"

        private val serverMappings = mapOf(
            "http://${BuildConfig.PLANET_URIUR_URL}" to "http://35.231.161.29",
            "http://192.168.1.202" to "http://34.35.29.147",
            "https://${BuildConfig.PLANET_GUATEMALA_URL}" to "http://guatemala.com/cloudserver",
            "http://${BuildConfig.PLANET_XELA_URL}" to "http://xela.com/cloudserver",
            "http://${BuildConfig.PLANET_SANPABLO_URL}" to "http://sanpablo.com/cloudserver",
            "http://${BuildConfig.PLANET_EMBAKASI_URL}" to "http://embakasi.com/cloudserver",
            "https://${BuildConfig.PLANET_VI_URL}" to "http://vi.com/cloudserver"
        )
    }

    suspend fun processSyncURL(onStartUpload: (String) -> Unit, onCreateAction: (Realm, String?, String?, String) -> Unit) {
        val startTime = System.currentTimeMillis()
        val url = Utilities.getUrl()
        Log.d(TAG, "Starting URL resolution process")
        Log.d(TAG, "Original URL: $url")

        val extractedUrl = extractBaseUrl(url)
        val primaryUrlMapping = findServerMapping(extractedUrl)

        if (primaryUrlMapping != null) {
            handleMappedUrls(primaryUrlMapping, url, onStartUpload, onCreateAction)
        } else {
            Log.w(TAG, "No mapping found for URL: $extractedUrl, using original")
            proceedWithOriginalUrl(url, onStartUpload, onCreateAction)
        }

        val totalDuration = System.currentTimeMillis() - startTime
        Log.d(TAG, "URL resolution process completed in ${totalDuration}ms")
    }

    private fun extractBaseUrl(url: String): String? {
        val regex = Regex("^(https?://).*?@(.*?):")
        return regex.find(url)?.let { matchResult ->
            val protocol = matchResult.groupValues[1]
            val address = matchResult.groupValues[2]
            "$protocol$address".also {
                Log.d(TAG, "URL components extracted - Protocol: $protocol, Address: $address")
            }
        }.also {
            if (it == null) Log.w(TAG, "Failed to extract URL components from: $url")
        }
    }

    private fun findServerMapping(extractedUrl: String?): Map.Entry<String, String>? {
        return serverMappings.entries.find { it.key.contains(extractedUrl ?: "") }
            .also { mapping ->
                if (mapping != null) {
                    Log.d(TAG, "Found mapping - Primary: ${mapping.key}, Alternative: ${mapping.value}")
                }
            }
    }

    private suspend fun handleMappedUrls(mapping: Map.Entry<String, String>, originalUrl: String, onStartUpload: (String) -> Unit, onCreateAction: (Realm, String?, String?, String) -> Unit) {
        val primaryUrl = mapping.key
        val alternativeUrl = mapping.value

        val isPrimaryReachable = checkPrimaryUrl(Utilities.getUrl())
        if (isPrimaryReachable) {
            Log.d(TAG, "Primary URL is reachable, proceeding with sync")
            proceedWithOriginalUrl(originalUrl, onStartUpload, onCreateAction)
        } else {
            handleAlternativeUrl(alternativeUrl, originalUrl, onStartUpload, onCreateAction)
        }
    }

    private suspend fun checkPrimaryUrl(url: String): Boolean {
        Log.d(TAG, "Testing primary URL reachability: $url")
        val startTime = System.currentTimeMillis()
        val isReachable = isServerReachable(url)
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Primary URL check took ${duration}ms, Result: $isReachable")
        return isReachable
    }

    private suspend fun handleAlternativeUrl(alternativeUrl: String, originalUrl: String, onStartUpload: (String) -> Unit, onCreateAction: (Realm, String?, String?, String) -> Unit) {
        Log.w(TAG, "Primary URL unreachable, attempting alternative")
        val uri = Uri.parse(alternativeUrl)
        Log.d(TAG, "Parsed alternative URI - Scheme: ${uri.scheme}, Host: ${uri.host}, Port: ${uri.port}")

        val (couchdbURL, urlUser, urlPwd) = processAlternativeUrl(uri, alternativeUrl)
        saveUrlPreferences(uri, originalUrl, couchdbURL, urlUser, urlPwd)

        val isAlternativeReachable = checkAlternativeUrl(couchdbURL)
        if (isAlternativeReachable) {
            proceedWithOriginalUrl(originalUrl, onStartUpload, onCreateAction)
        } else {
            Log.e(TAG, "Both URLs are unreachable")
        }
    }

    private fun processAlternativeUrl(uri: Uri, alternativeUrl: String): Triple<String, String, String> {
        return if (alternativeUrl.contains("@")) {
            Log.d(TAG, "Alternative URL contains credentials, extracting")
            val userinfo = ProcessUserDataActivity.getUserInfo(uri)
            Triple(alternativeUrl, userinfo[0], userinfo[1])
        } else {
            Log.d(TAG, "Building alternative URL with default credentials")
            val user = "satellite"
            val pwd = settings.getString("serverPin", "") ?: ""
            val url = "${uri.scheme}://$user:$pwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
            Triple(url, user, pwd)
        }
    }

    private fun saveUrlPreferences(uri: Uri, originalUrl: String, couchdbURL: String, urlUser: String, urlPwd: String) {
        editor.apply {
            putString("url_user", urlUser)
            putString("url_pwd", urlPwd)
            putString("url_Scheme", uri.scheme)
            putString("url_Host", uri.host)
            putString("alternativeUrl", originalUrl)
            putString("processedAlternativeUrl", couchdbURL)
            putBoolean("isAlternativeUrl", true)
            apply()
        }
        Log.d(TAG, "Saved alternative URL settings to preferences")
    }

    private suspend fun checkAlternativeUrl(url: String): Boolean {
        Log.d(TAG, "Testing alternative URL reachability")
        val startTime = System.currentTimeMillis()
        val isReachable = isServerReachable(url)
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Alternative URL check took ${duration}ms, Result: $isReachable")
        return isReachable
    }

    private fun proceedWithOriginalUrl(url: String, onStartUpload: (String) -> Unit, onCreateAction: (Realm, String?, String?, String) -> Unit) {
        onStartUpload("dashboard")
        onCreateAction(mRealm, "${profileDbHandler.userModel?.id}", null, "sync")
    }
}