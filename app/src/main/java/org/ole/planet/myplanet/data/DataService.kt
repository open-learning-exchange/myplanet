package org.ole.planet.myplanet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.JsonObject
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SecurityDataListener
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.di.ApiInterfaceEntryPoint
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.ApplicationScopeEntryPoint
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.di.DatabaseServiceEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Sha256Utils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DataService constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @ApplicationScope private val serviceScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val uploadToShelfService: UploadToShelfService,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val configurationManager =
        ConfigurationManager(context, preferences, retrofitInterface)

    fun becomeMember(
        obj: JsonObject,
        callback: CreateUserCallback,
        securityCallback: SecurityDataListener? = null
    ) {
        serviceScope.launch {
            val result = userRepository.becomeMember(obj)
            withContext(Dispatchers.Main) {
                if (result.first) { // success
                    if (context is ProcessUserDataActivity) {
                        val userName = obj["name"].asString
                        context.startUpload("becomeMember", userName, securityCallback)
                    }

                    // Handle offline logic regardless of context
                    if (result.second == context.getString(R.string.not_connect_to_planet_created_user_offline)) {
                        Utilities.toast(MainApplication.context, result.second)
                        securityCallback?.onSecurityDataUpdated()
                    }

                    callback.onSuccess(result.second)
                } else {
                    // failure
                    callback.onSuccess(result.second)
                    securityCallback?.onSecurityDataUpdated()
                }
            }
        }
    }

    suspend fun syncPlanetServers(callback: SuccessListener) {
        try {
            val response = withContext(Dispatchers.IO) {
                retrofitInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true").execute()
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

    fun getMinApk(
        listener: ConfigurationIdListener?,
        url: String,
        pin: String,
        activity: SyncActivity,
        callerActivity: String
    ) {
        configurationManager.getMinApk(listener, url, pin, activity, callerActivity)
    }

    interface CreateUserCallback {
        fun onSuccess(message: String)
    }

    interface ConfigurationIdListener {
        fun onConfigurationIdReceived(
            id: String,
            code: String,
            url: String,
            defaultUrl: String,
            isAlternativeUrl: Boolean,
            callerActivity: String
        )
    }
}
