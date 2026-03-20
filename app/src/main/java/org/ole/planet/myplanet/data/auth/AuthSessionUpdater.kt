package org.ole.planet.myplanet.data.auth

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils

class AuthSessionUpdater @AssistedInject constructor(
    @Assisted private val callback: AuthCallback,
    private val sharedPrefManager: SharedPrefManager,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) {

    interface AuthCallback {
        fun setAuthSession(responseHeader: Map<String, List<String>>)
        fun onError(s: String)
    }

    @AssistedFactory
    interface Factory {
        fun create(callback: AuthCallback): AuthSessionUpdater
    }

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                sendPost()
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
    private suspend fun sendPost() {
        try {
            withContext(dispatcherProvider.io) {
                val conn = getSessionUrl()?.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.doInput = true

                val os = DataOutputStream(conn.outputStream)
                os.writeBytes(getJsonObject().toString())

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

    private fun getJsonObject(): JSONObject? {
        return try {
            val jsonParam = JSONObject()
            jsonParam.put("name", sharedPrefManager.getUrlUser())
            jsonParam.put("password", sharedPrefManager.getUrlPwd())
            jsonParam
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getSessionUrl(): URL? {
        return try {
            val pref = UrlUtils.getUrl()
            val urlString = "$pref/_session"
            val serverUrl = URL(urlString)
            serverUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
