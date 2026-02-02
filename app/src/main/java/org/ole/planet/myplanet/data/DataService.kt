package org.ole.planet.myplanet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.JsonObject
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSecurityDataListener
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApiInterfaceEntryPoint
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.ApplicationScopeEntryPoint
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.di.DatabaseServiceEntryPoint
import org.ole.planet.myplanet.di.RepositoryEntryPoint
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ConfigurationManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Sha256Utils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class DataService constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @ApplicationScope private val serviceScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val uploadToShelfService: UploadToShelfService,
) {
    constructor(context: Context) : this(
        context,
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApiInterfaceEntryPoint::class.java
        ).apiInterface(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseServiceEntryPoint::class.java
        ).databaseService(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplicationScopeEntryPoint::class.java
        ).applicationScope(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoryEntryPoint::class.java
        ).userRepository(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoSyncEntryPoint::class.java
        ).uploadToShelfService(),
    )

    private val preferences: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val configurationManager =
        ConfigurationManager(context, preferences, retrofitInterface)

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

    @Deprecated("Use ConfigurationsRepository.checkServerAvailability instead")
    fun isPlanetAvailable(callback: PlanetAvailableListener?) {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        serverAvailabilityCache[updateUrl]?.let { (available, timestamp) ->
            if (System.currentTimeMillis() - timestamp < 30000) {
                if (available) {
                    callback?.isAvailable()
                } else {
                    callback?.notAvailable()
                }
                return
            }
        }

        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                val primaryReachable = isServerReachable(mapping.primaryUrl)
                val alternativeReachable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

                if (!primaryReachable && alternativeReachable) {
                    mapping.alternativeUrl?.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        val editor = preferences.edit()

                        serverUrlMapper.updateUrlPreferences(
                            editor,
                            uri,
                            alternativeUrl,
                            mapping.primaryUrl,
                            preferences
                        )
                    }
                }
            }

            try {
                val response = retrofitInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences))
                val isAvailable = callback != null && response.code() == 200
                serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    if (isAvailable) {
                        callback.isAvailable()
                    } else {
                        callback?.notAvailable()
                    }
                }
            } catch (e: Exception) {
                serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    callback?.notAvailable()
                }
            }
        }
    }

    fun becomeMember(obj: JsonObject, callback: CreateUserCallback, securityCallback: OnSecurityDataListener? = null) {
        serviceScope.launch {
            val result = userRepository.becomeMember(obj)
            withContext(Dispatchers.Main) {
                if (result.first) {
                    if (context is ProcessUserDataActivity) {
                        val userName = obj["name"].asString
                        context.startUpload("becomeMember", userName, securityCallback)
                    }

                    if (result.second == context.getString(R.string.not_connect_to_planet_created_user_offline)) {
                        Utilities.toast(MainApplication.context, result.second)
                        securityCallback?.onSecurityDataUpdated()
                    }

                    callback.onSuccess(result.second)
                } else {
                    callback.onSuccess(result.second)
                    securityCallback?.onSecurityDataUpdated()
                }
            }
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
                    withContext(Dispatchers.IO) {
                        databaseService.withRealm { backgroundRealm ->
                            backgroundRealm.executeTransaction { realm1 ->
                                realm1.delete(RealmCommunity::class.java)
                                for (j in arr) {
                                    var jsonDoc = j.asJsonObject
                                    jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
                                    val id = JsonUtils.getString("_id", jsonDoc)
                                    val community = realm1.createObject(RealmCommunity::class.java, id)
                                    if (JsonUtils.getString("name", jsonDoc) == "learning") {
                                        community.weight = 0
                                    }
                                    community.localDomain = JsonUtils.getString("localDomain", jsonDoc)
                                    community.name = JsonUtils.getString("name", jsonDoc)
                                    community.parentDomain = JsonUtils.getString("parentDomain", jsonDoc)
                                    community.registrationRequest = JsonUtils.getString("registrationRequest", jsonDoc)
                                }
                            }
                        }
                    }
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

    interface CreateUserCallback {
        fun onSuccess(message: String)
    }

    interface PlanetAvailableListener {
        fun isAvailable()
        fun notAvailable()
    }

    interface ConfigurationIdListener {
        fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String)
    }
}
