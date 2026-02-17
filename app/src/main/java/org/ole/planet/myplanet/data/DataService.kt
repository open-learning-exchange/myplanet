package org.ole.planet.myplanet.data

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ConfigurationManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Sha256Utils
import org.ole.planet.myplanet.utils.UrlUtils

class DataService constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @param:ApplicationScope private val serviceScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val uploadToShelfService: UploadToShelfService,
    private val communityRepository: CommunityRepository,
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val configurationManager =
        ConfigurationManager(context, preferences, retrofitInterface)

    @Deprecated("Use ConfigurationsRepository.checkHealth instead")
    fun healthAccess(listener: OnSuccessListener) {
        serviceScope.launch {
            try {
                val healthUrl = UrlUtils.getHealthAccessUrl(preferences)
                if (healthUrl.isBlank()) {
                    withContext(Dispatchers.Main) { listener.onSuccess("") }
                    return@launch
                }

                try {
                    val response = retrofitInterface.healthAccess(healthUrl)
                    withContext(Dispatchers.Main) {
                        when (response.code()) {
                            200 -> listener.onSuccess(context.getString(R.string.server_sync_successfully))
                            401 -> listener.onSuccess("Unauthorized - Invalid credentials")
                            404 -> listener.onSuccess("Server endpoint not found")
                            500 -> listener.onSuccess("Server internal error")
                            502 -> listener.onSuccess("Bad gateway - Server unavailable")
                            503 -> listener.onSuccess("Service temporarily unavailable")
                            504 -> listener.onSuccess("Gateway timeout")
                            else -> listener.onSuccess("Server error: ${response.code()}")
                        }
                    }
                } catch (t: Exception) {
                    t.printStackTrace()
                    val errorMsg = when (t) {
                        is java.net.UnknownHostException -> "Server not reachable"
                        is java.net.SocketTimeoutException -> "Connection timeout"
                        is java.net.ConnectException -> "Unable to connect to server"
                        is java.io.IOException -> "Network connection error"
                        else -> "Network error: ${t.localizedMessage ?: "Unknown error"}"
                    }
                    withContext(Dispatchers.Main) { listener.onSuccess(errorMsg) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { listener.onSuccess("Health access initialization failed") }
            }
        }
    }

    @Deprecated("Use ConfigurationsRepository.checkCheckSum instead")
    suspend fun checkCheckSum(path: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = retrofitInterface.getChecksum(UrlUtils.getChecksumUrl(preferences))
            if (response.isSuccessful) {
                val checksum = response.body()?.string()
                if (!checksum.isNullOrEmpty()) {
                    val f = FileUtils.getSDPathFromUrl(context, path)
                    if (f.exists()) {
                        val sha256 = Sha256Utils().getCheckSumFromFile(f)
                        return@withContext checksum.contains(sha256)
                    }
                }
            }
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }


    suspend fun syncPlanetServers(callback: OnSuccessListener) {
        try {
            val response = withContext(Dispatchers.IO) {
                retrofitInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")
            }

            if (response.isSuccessful && response.body() != null) {
                val arr = JsonUtils.getJsonArray("rows", response.body())
                val startTime = System.currentTimeMillis()
                println("Realm transaction started")

                val transactionResult = runCatching {
                    communityRepository.replaceAll(arr)
                }

                val endTime = System.currentTimeMillis()
                println("Realm transaction finished in ${endTime - startTime}ms")

                withContext(Dispatchers.Main) {
                    transactionResult.onSuccess {
                        callback.onSuccess(context.getString(R.string.server_sync_successfully))
                    }.onFailure { e ->
                        e.printStackTrace()
                        callback.onSuccess(context.getString(R.string.server_sync_has_failed))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback.onSuccess(context.getString(R.string.server_sync_has_failed))
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            withContext(Dispatchers.Main) {
                callback.onSuccess(context.getString(R.string.server_sync_has_failed))
            }
        }
    }

    fun getMinApk(listener: ConfigurationIdListener?, url: String, pin: String, activity: SyncActivity, callerActivity: String) {
        configurationManager.getMinApk(listener, url, pin, activity, callerActivity)
    }


    interface ConfigurationIdListener {
        fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String)
    }
}
