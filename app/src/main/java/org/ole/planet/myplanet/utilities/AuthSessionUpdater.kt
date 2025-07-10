package org.ole.planet.myplanet.utilities

import android.content.SharedPreferences
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuthSessionUpdater(
    private val callback: AuthCallback,
    private val settings: SharedPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    interface AuthCallback {
        fun setAuthSession(responseHeader: Map<String, List<String>>)
        fun onError(s: String)
    }

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                sendPost(settings)
                delay(15 * 60 * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    // sendPost() - Meant to get New AuthSession Token for viewing Online resources such as Video, and basically any file.
    // It creates a session of about 20 mins after which a new AuthSession Token will be needed.
    // During these 20 mins items.getResourceRemoteAddress() will work in obtaining the files necessary.
    private suspend fun sendPost(settings: SharedPreferences) {
        try {
            withContext(Dispatchers.IO) {
                val conn = getSessionUrl()?.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.doInput = true

                val os = DataOutputStream(conn.outputStream)
                os.writeBytes(getJsonObject(settings).toString())

                os.flush()
                os.close()

                callback.setAuthSession(conn.headerFields)
                conn.disconnect()
            }
        } catch (e: Exception) {
            callback.onError(e.message.orEmpty())
            e.printStackTrace()
        }
    }

    private fun getJsonObject(settings: SharedPreferences): JSONObject? {
        return try {
            val jsonParam = JSONObject()
            jsonParam.put("name", settings.getString("url_user", ""))
            jsonParam.put("password", settings.getString("url_pwd", ""))
            jsonParam
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getSessionUrl(): URL? {
        return try {
            val pref = Utilities.getUrl()
            val urlString = "$pref/_session"
            val serverUrl = URL(urlString)
            serverUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
