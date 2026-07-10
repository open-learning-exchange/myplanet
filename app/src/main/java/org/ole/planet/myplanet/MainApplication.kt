package org.ole.planet.myplanet

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.TrafficStats
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration as WorkManagerConfiguration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.di.DefaultPreferences
import org.ole.planet.myplanet.di.NetworkDependenciesEntryPoint
import org.ole.planet.myplanet.di.RealmDispatcherProvider
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.AutoSyncWorker
import org.ole.planet.myplanet.services.NetworkMonitorWorker
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.TaskNotificationWorker
import org.ole.planet.myplanet.services.ThemeManager
import org.ole.planet.myplanet.services.retry.RetryQueueWorker
import org.ole.planet.myplanet.utils.ANRWatchdog
import org.ole.planet.myplanet.utils.Constants.NETWORK_TRAFFIC_TAG
import org.ole.planet.myplanet.utils.CrashLogStore
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LocaleUtils
import org.ole.planet.myplanet.utils.MarkdownUtils
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utils.NetworkUtils.startListenNetworkState
import org.ole.planet.myplanet.utils.NetworkUtils.stopListenNetworkState
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.ThemeMode
import org.ole.planet.myplanet.utils.UrlUtils.init
import org.ole.planet.myplanet.utils.VersionUtils.getVersionName

@HiltAndroidApp
class MainApplication : Application(), WorkManagerConfiguration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var realmDispatcherProvider: RealmDispatcherProvider

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var databaseServiceProvider: Provider<DatabaseService>
    val databaseService: DatabaseService by lazy { databaseServiceProvider.get() }

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    @DefaultPreferences
    lateinit var defaultPreferencesProvider: Provider<SharedPreferences>
    val defaultPref: SharedPreferences by lazy { defaultPreferencesProvider.get() }

    @Inject
    lateinit var resourcesRepository: ResourcesRepository

    @Inject
    lateinit var resourceDownloadCoordinator: ResourceDownloadCoordinator

    override val workManagerConfiguration: WorkManagerConfiguration
        get() = WorkManagerConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val AUTO_SYNC_WORK_TAG = "autoSyncWork"
        private const val TASK_NOTIFICATION_WORK_TAG = "taskNotificationWork"
        private const val ANR_LOG_TYPE = "anr"
        private lateinit var instance: MainApplication

        @VisibleForTesting
        var testContext: Context? = null

        val context: Context get() = testContext ?: instance.applicationContext
        var syncFailedCount = 0
        var isCollectionSwitchOn = false
        var showDownload = false
        val isSyncRunning = AtomicBoolean(false)
        private var _listener: WeakReference<OnTeamPageListener>? = null
        var listener: OnTeamPageListener?
            get() = _listener?.get()
            set(value) { _listener = value?.let { WeakReference(it) } }
        val androidId: String get() {
            try {
                return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "0"
        }
        lateinit var applicationScope: CoroutineScope

        val coreDependenciesEntryPoint: CoreDependenciesEntryPoint by lazy {
            EntryPointAccessors.fromApplication(context, CoreDependenciesEntryPoint::class.java)
        }

        fun createLog(type: String, error: String = "") {
            applicationScope.launch {
                saveLogToRealm(type, error, "${Date().time}")
            }
        }

        // A report for a failure that may kill the process (crash/ANR) must be persisted
        // to a plain file before this runs: the Realm write below waits on the shared
        // write lock and dispatcher, so it can be lost if the process dies first.
        suspend fun saveLogToRealm(type: String, error: String, time: String): Boolean {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                CoreDependenciesEntryPoint::class.java
            )
            val userSessionManager = entryPoint.userSessionManager()
            val spm = entryPoint.sharedPrefManager()
            return try {
                val databaseService = (context.applicationContext as MainApplication).databaseService
                val model = userSessionManager.getUserModel()
                databaseService.executeTransactionAsync { r ->
                    val log = r.createObject(RealmApkLog::class.java, "${UUID.randomUUID()}")
                    log.parentCode = spm.getParentCode()
                    log.createdOn = spm.getPlanetCode()
                    model?.let { log.userId = it.id }
                    log.time = time
                    log.page = ""
                    log.version = getVersionName(context)
                    log.type = type
                    if (error.isNotEmpty()) {
                        log.error = error
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun applyThemeMode(themeMode: String?) {
            when (themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        suspend fun isServerReachable(
            urlString: String,
            ioDispatcher: CoroutineDispatcher = coreDependenciesEntryPoint.dispatcherProvider().io
        ): Boolean {
            if (urlString.isBlank()) return false
            val serverUrlMapper = coreDependenciesEntryPoint.serverUrlMapper()
            val mapping = serverUrlMapper.processUrl(urlString)
            val urlsToTry = mutableListOf(urlString)
            mapping.alternativeUrl?.let { urlsToTry.add(it) }

            for (url in urlsToTry) {
                val reachable = tryConnect(url, ioDispatcher)
                if (reachable) return true
            }
            return false
        }

        private suspend fun tryConnect(
            urlString: String,
            ioDispatcher: CoroutineDispatcher
        ): Boolean {
            return try {
                val formattedUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    "http://$urlString"
                } else {
                    urlString
                }
                val url = URL(formattedUrl)
                val responseCode = withContext(ioDispatcher) {
                    TrafficStats.setThreadStatsTag(NETWORK_TRAFFIC_TAG)
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.connect()
                        connection.responseCode
                    } finally {
                        connection.disconnect()
                        TrafficStats.clearThreadStatsTag()
                    }
                }
                responseCode in 200..299
            } catch (e: Exception) {
                false
            }
        }

        fun handleUncaughtException(e: Throwable) {
            e.printStackTrace()
            val error = e.stackTraceToString()
            val pendingFile = CrashLogStore.save(context, RealmApkLog.ERROR_TYPE_CRASH, error)
            applicationScope.launch {
                if (saveLogToRealm(RealmApkLog.ERROR_TYPE_CRASH, error, "${Date().time}")) {
                    pendingFile?.delete()
                }
            }

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }
    }

    private var mainThreadRealm: io.realm.Realm? = null
    private var isFirstLaunch = true
    private lateinit var anrWatchdog: ANRWatchdog

    override fun onCreate() {
        super.onCreate()
        instance = this
        init(sharedPrefManager)
        setupCriticalProperties()
        LocaleUtils.preload(this)
        warmUpMainThreadRealm()
        performDeferredInitialization()
        setupStrictMode()
        registerExceptionHandler()
        setupLifecycleCallbacks()
    }

    private fun performDeferredInitialization() {
        applicationScope.launch(dispatcherProvider.io) {
            FileUtils.warmUp(this@MainApplication)
            SecurePrefs.warmUp(this@MainApplication)
            MarkdownUtils.warmUp(this@MainApplication)
            runCatching { Class.forName("pl.droidsonroids.gif.GifInfoHandle") }
        }
        applicationScope.launch {
            initApp()
            loadAndApplyTheme()
            initializeDatabaseConnection()
            sweepPendingLogs()
            setupAnrWatchdog()
            scheduleWorkersOnStart()
            observeNetworkForDownloads()
        }
    }

    private suspend fun sweepPendingLogs() {
        try {
            val pendingLogs = withContext(dispatcherProvider.io) {
                CrashLogStore.loadPendingLogs(this@MainApplication)
            }
            for (pending in pendingLogs) {
                if (saveLogToRealm(pending.type, pending.error, pending.time)) {
                    withContext(dispatcherProvider.io) { pending.file.delete() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun initApp() {
        applicationScope.launch(dispatcherProvider.default) {
            startListenNetworkState()
        }
    }

    private fun setupCriticalProperties() {
        applicationScope = EntryPointAccessors.fromApplication(
            this,
            CoreDependenciesEntryPoint::class.java
        ).applicationScope()
    }

    private fun warmUpMainThreadRealm() {
        try {
            mainThreadRealm = databaseService.createManagedRealmInstance()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun initializeDatabaseConnection() {
        databaseService.withRealmAsync { }
    }

    private fun setupStrictMode() {
        if (BuildConfig.DEBUG) {
            val threadPolicy = StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            StrictMode.setThreadPolicy(threadPolicy)

            val vmPolicy = VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            StrictMode.setVmPolicy(vmPolicy)
        }
    }

    private suspend fun setupAnrWatchdog() {
        withContext(dispatcherProvider.default) {
            anrWatchdog = ANRWatchdog(
                timeout = 5000L,
                listener = object : ANRWatchdog.ANRListener {
                    override fun onAppNotResponding(message: String, blockedThread: Thread, duration: Long) {
                        val error = "ANR detected! Duration: ${duration}ms\n $message"
                        val pendingFile = CrashLogStore.save(context, ANR_LOG_TYPE, error)
                        applicationScope.launch {
                            if (saveLogToRealm(ANR_LOG_TYPE, error, "${Date().time}")) {
                                pendingFile?.delete()
                            }
                        }
                    }
                },
                dispatcherProvider = dispatcherProvider
            )
            anrWatchdog.start()
        }
    }

    private suspend fun scheduleWorkersOnStart() {
        withContext(dispatcherProvider.default) {
            if (sharedPrefManager.getAutoSync()) {
                scheduleAutoSyncWork(sharedPrefManager.getAutoSyncInterval())
            } else {
                cancelAutoSyncWork()
            }
            scheduleTaskNotificationWork()
            startNetworkMonitoring()
            scheduleRetryQueueWork()
        }
    }

    private fun scheduleRetryQueueWork() {
        // Recover any operations stuck in "in_progress" from previous crash
        applicationScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@MainApplication,
                    NetworkDependenciesEntryPoint::class.java
                )
                entryPoint.retryQueue().recoverStuckOperations()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        RetryQueueWorker.schedule(this)
    }

    private fun registerExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, e: Throwable ->
            handleUncaughtException(e)
        }
    }

    private fun setupLifecycleCallbacks() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                onAppForegrounded()
            }
        })
        onAppStarted()
    }

    private suspend fun loadAndApplyTheme() {
        try {
            val savedThemeMode = withContext(dispatcherProvider.io) {
                getCurrentThemeMode()
            }
            applyThemeMode(savedThemeMode)
        } finally {
            // success
        }
    }

    private suspend fun observeNetworkForDownloads() {
        withContext(dispatcherProvider.default) {
            isNetworkConnectedFlow.onEach { isConnected ->
                if (isConnected) {
                    val serverUrl = sharedPrefManager.getServerUrl()
                    if (serverUrl.isNotEmpty()) {
                        applicationScope.launch {
                            val canReachServer = isServerReachable(serverUrl, dispatcherProvider.io)
                            if (canReachServer && defaultPref.getBoolean("beta_auto_download", false)) {
                                resourceDownloadCoordinator.startBackgroundDownload(
                                    downloadAllFiles(resourcesRepository.getAllLibrariesToSync())
                                )
                            }
                        }
                    }
                }
            }.launchIn(applicationScope)
        }
    }

    fun applyAutoSyncSettings() {
        if (sharedPrefManager.getAutoSync()) {
            scheduleAutoSyncWork(sharedPrefManager.getAutoSyncInterval())
        } else {
            cancelAutoSyncWork()
        }
    }

    private fun scheduleAutoSyncWork(syncInterval: Int?) {
        if (syncInterval == null) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val autoSyncWork = PeriodicWorkRequest.Builder(AutoSyncWorker::class.java, syncInterval.toLong(), TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(AUTO_SYNC_WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, autoSyncWork)
    }

    private fun cancelAutoSyncWork() {
        WorkManager.getInstance(this).cancelUniqueWork(AUTO_SYNC_WORK_TAG)
    }

    private fun scheduleTaskNotificationWork() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val taskNotificationWork: PeriodicWorkRequest = PeriodicWorkRequest.Builder(TaskNotificationWorker::class.java, 900, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(TASK_NOTIFICATION_WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, taskNotificationWork)
    }

    private fun startNetworkMonitoring() {
        NetworkMonitorWorker.start(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.onAttach(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (getCurrentThemeMode() != ThemeMode.FOLLOW_SYSTEM) return

        val isNightMode = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeToApply = if (isNightMode) ThemeMode.DARK else ThemeMode.LIGHT

        applyThemeMode(themeToApply)
    }

    private fun getCurrentThemeMode(): String {
        return ThemeManager.getCurrentThemeMode(context)
    }

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
        mainThreadRealm?.close()
        mainThreadRealm = null
        super.onTerminate()
        stopListenNetworkState()
    }
}
