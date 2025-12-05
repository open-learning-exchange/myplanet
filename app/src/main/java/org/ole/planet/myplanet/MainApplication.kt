package org.ole.planet.myplanet

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.backgroundDownload
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.getAllLibraryList
import org.ole.planet.myplanet.callback.TeamPageListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.ApiClientEntryPoint
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScopeEntryPoint
import org.ole.planet.myplanet.di.DefaultPreferences
import org.ole.planet.myplanet.di.WorkerDependenciesEntryPoint
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.service.AutoSyncWorker
import org.ole.planet.myplanet.service.NetworkMonitorWorker
import org.ole.planet.myplanet.service.StayOnlineWorker
import org.ole.planet.myplanet.service.TaskNotificationWorker
import org.ole.planet.myplanet.utilities.ANRWatchdog
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utilities.NetworkUtils.startListenNetworkState
import org.ole.planet.myplanet.utilities.NetworkUtils.stopListenNetworkState
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.ThemeMode
import org.ole.planet.myplanet.utilities.VersionUtils.getVersionName

@HiltAndroidApp
class MainApplication : Application(), Application.ActivityLifecycleCallbacks {
    @Inject
    lateinit var databaseServiceProvider: Provider<DatabaseService>
    val databaseService: DatabaseService by lazy { databaseServiceProvider.get() }

    @Inject
    @AppPreferences
    lateinit var appPreferencesProvider: Provider<SharedPreferences>
    val preferences: SharedPreferences by lazy { appPreferencesProvider.get() }

    @Inject
    @DefaultPreferences
    lateinit var defaultPreferencesProvider: Provider<SharedPreferences>
    val defaultPref: SharedPreferences by lazy { defaultPreferencesProvider.get() }

    companion object {
        private const val AUTO_SYNC_WORK_TAG = "autoSyncWork"
        private const val STAY_ONLINE_WORK_TAG = "stayOnlineWork"
        private const val TASK_NOTIFICATION_WORK_TAG = "taskNotificationWork"
        lateinit var context: Context
        var syncFailedCount = 0
        var isCollectionSwitchOn = false
        var showDownload = false
        var isSyncRunning = false
        var listener: TeamPageListener? = null
        val androidId: String get() {
            try {
                return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "0"
        }
        lateinit var applicationScope: CoroutineScope
        val apiClientInitialized = CompletableDeferred<Unit>()

        fun createLog(type: String, error: String = "") {
            applicationScope.launch {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context,
                    WorkerDependenciesEntryPoint::class.java
                )
                val userProfileDbHandler = entryPoint.userProfileDbHandler()
                val settings = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                try {
                    val databaseService = (context.applicationContext as MainApplication).databaseService
                    databaseService.executeTransactionAsync { r ->
                        val log = r.createObject(RealmApkLog::class.java, "${UUID.randomUUID()}")
                        val model = userProfileDbHandler.userModel
                        log.parentCode = settings.getString("parentCode", "")
                        log.createdOn = settings.getString("planetCode", "")
                        model?.let { log.userId = it.id }
                        log.time = "${Date().time}"
                        log.page = ""
                        log.version = getVersionName(context)
                        log.type = type
                        if (error.isNotEmpty()) {
                            log.error = error
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun applyThemeMode(themeMode: String?) {
            when (themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        suspend fun isServerReachable(urlString: String): Boolean {
            val serverUrlMapper = ServerUrlMapper()
            val mapping = serverUrlMapper.processUrl(urlString)
            val urlsToTry = mutableListOf(urlString)
            mapping.alternativeUrl?.let { urlsToTry.add(it) }

            return try {
                if (urlString.isBlank()) return false

                val formattedUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    "http://$urlString"
                } else {
                    urlString
                }

                val url = URL(formattedUrl)
                val responseCode = withContext(Dispatchers.IO) {
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.connect()
                        connection.responseCode
                    } finally {
                        connection.disconnect()
                    }
                }
                responseCode in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun handleUncaughtException(e: Throwable) {
            e.printStackTrace()
            createLog(RealmApkLog.ERROR_TYPE_CRASH, e.stackTraceToString())

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }
    }

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    private var isFirstLaunch = true
    private lateinit var anrWatchdog: ANRWatchdog

    override fun onCreate() {
        super.onCreate()
        context = this
        setupCriticalProperties()
        performDeferredInitialization()
        setupStrictMode()
        registerExceptionHandler()
        setupLifecycleCallbacks()
    }

    private fun performDeferredInitialization() {
        applicationScope.launch {
            initApp()
            loadAndApplyTheme()
            ensureApiClientInitialized()
            initializeDatabaseConnection()
            setupAnrWatchdog()
            scheduleWorkersOnStart()
            observeNetworkForDownloads()
        }
    }
    private fun initApp() {
        applicationScope.launch(Dispatchers.Default) {
            startListenNetworkState()
        }
    }

    private fun setupCriticalProperties() {
        applicationScope = EntryPointAccessors.fromApplication(
            this,
            ApplicationScopeEntryPoint::class.java
        ).applicationScope()
    }

    private suspend fun ensureApiClientInitialized() {
        withContext(Dispatchers.IO) {
            EntryPointAccessors.fromApplication(
                this@MainApplication,
                ApiClientEntryPoint::class.java
            ).apiClient()
            apiClientInitialized.complete(Unit)
        }
    }
    
    private suspend fun initializeDatabaseConnection() {
        withContext(Dispatchers.IO) {
            databaseService.withRealm { }
        }
    }

    private fun setupStrictMode() {
        if (BuildConfig.DEBUG) {
            val threadPolicy = StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .detectDiskReads()
                .penaltyLog()
                .build()
            StrictMode.setThreadPolicy(threadPolicy)

            val vmPolicy = VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
            StrictMode.setVmPolicy(vmPolicy)
        }
    }

    private suspend fun setupAnrWatchdog() {
        withContext(Dispatchers.Default) {
            anrWatchdog = ANRWatchdog(timeout = 5000L, listener = object : ANRWatchdog.ANRListener {
                override fun onAppNotResponding(message: String, blockedThread: Thread, duration: Long) {
                    applicationScope.launch {
                        createLog("anr", "ANR detected! Duration: ${duration}ms\n $message")
                    }
                }
            })
            anrWatchdog.start()
        }
    }

    private suspend fun scheduleWorkersOnStart() {
        withContext(Dispatchers.Default) {
            if (preferences.getBoolean("autoSync", false) && preferences.contains("autoSyncInterval")) {
                val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
                scheduleAutoSyncWork(syncInterval)
            } else {
                cancelAutoSyncWork()
            }
            scheduleStayOnlineWork()
            scheduleTaskNotificationWork()
            startNetworkMonitoring()
        }
    }

    private fun registerExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, e: Throwable ->
            handleUncaughtException(e)
        }
    }

    private fun setupLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(this)
        onAppStarted()
    }

    private suspend fun loadAndApplyTheme() {
        try {
            val savedThemeMode = withContext(Dispatchers.IO) {
                getCurrentThemeMode()
            }
            applyThemeMode(savedThemeMode)
        } finally {
            // success
        }
    }

    private suspend fun observeNetworkForDownloads() {
        withContext(Dispatchers.Default) {
            isNetworkConnectedFlow.onEach { isConnected ->
                if (isConnected) {
                    val serverUrl = preferences.getString("serverURL", "")
                    if (!serverUrl.isNullOrEmpty()) {
                        applicationScope.launch {
                            val canReachServer = withContext(Dispatchers.IO) {
                                isServerReachable(serverUrl)
                            }
                            if (canReachServer && defaultPref.getBoolean("beta_auto_download", false)) {
                                databaseService.withRealm { realm ->
                                    backgroundDownload(
                                        downloadAllFiles(getAllLibraryList(realm)),
                                        applicationContext
                                    )
                                }
                            }
                        }
                    }
                }
            }.launchIn(applicationScope)
        }
    }

    private fun scheduleAutoSyncWork(syncInterval: Int?) {
        val autoSyncWork: PeriodicWorkRequest? = syncInterval?.let { PeriodicWorkRequest.Builder(AutoSyncWorker::class.java, it.toLong(), TimeUnit.SECONDS).build() }
        val workManager = WorkManager.getInstance(this)
        if (autoSyncWork != null) {
            workManager.enqueueUniquePeriodicWork(AUTO_SYNC_WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, autoSyncWork)
        }
    }

    private fun cancelAutoSyncWork() {
        val workManager = WorkManager.getInstance(this)
        workManager.cancelUniqueWork(AUTO_SYNC_WORK_TAG)
    }

    private fun scheduleStayOnlineWork() {
        val stayOnlineWork: PeriodicWorkRequest = PeriodicWorkRequest.Builder(StayOnlineWorker::class.java, 900, TimeUnit.SECONDS).build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(STAY_ONLINE_WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, stayOnlineWork)
    }

    private fun scheduleTaskNotificationWork() {
        val taskNotificationWork: PeriodicWorkRequest = PeriodicWorkRequest.Builder(TaskNotificationWorker::class.java, 900, TimeUnit.SECONDS).build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(TASK_NOTIFICATION_WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, taskNotificationWork)
    }

    private fun startNetworkMonitoring() {
        NetworkMonitorWorker.start(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (getCurrentThemeMode() != ThemeMode.FOLLOW_SYSTEM) return

        val isNightMode = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeToApply = if (isNightMode) ThemeMode.DARK else ThemeMode.LIGHT

        applyThemeMode(themeToApply)
    }

    private fun getCurrentThemeMode(): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getString("theme_mode", ThemeMode.FOLLOW_SYSTEM) ?: ThemeMode.FOLLOW_SYSTEM
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            onAppForegrounded()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (isFirstLaunch) {
            isFirstLaunch = false
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        --activityReferences
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    private fun onAppForegrounded() {
        if (isFirstLaunch) {
            isFirstLaunch = false
        } else {
            applicationScope.launch {
                createLog("foreground", "")
            }
        }
    }

    private fun onAppStarted() {
        applicationScope.launch {
            createLog("new login", "")
        }
    }

    override fun onTerminate() {
        if (::anrWatchdog.isInitialized) {
            anrWatchdog.stop()
        }
        super.onTerminate()
        stopListenNetworkState()
    }
}
