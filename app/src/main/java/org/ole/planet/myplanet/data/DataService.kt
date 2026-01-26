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
import okhttp3.ResponseBody
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.ApiInterfaceEntryPoint
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.ApplicationScopeEntryPoint
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.di.DatabaseServiceEntryPoint
import org.ole.planet.myplanet.di.RepositoryEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ConfigurationManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils
import retrofit2.Response

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

    fun getMinApk(listener: ConfigurationIdListener?, url: String, pin: String, activity: SyncActivity, callerActivity: String) {
        configurationManager.getMinApk(listener, url, pin, activity, callerActivity)
    }

    interface ConfigurationIdListener {
        fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String)
    }
}
