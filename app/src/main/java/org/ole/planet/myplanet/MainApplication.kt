package org.ole.planet.myplanet

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.TrafficStats
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.Settings
import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.di.DefaultPreferences
import org.ole.planet.myplanet.di.ServiceDependenciesEntryPoint
import org.ole.planet.myplanet.model.ApkLog
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
import org.ole.planet.myplanet.utils.PdfThumbnailLoader
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.ThemeMode
import org.ole.planet.myplanet.utils.UrlUtils.init

@HiltAndroidApp
class MainApplication : Application(), WorkManagerConfiguration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var appDatabaseProvider: Provider<AppDatabase>
    val appDatabase: AppDatabase by lazy { appDatabaseProvider.get() }

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var themeManager: ThemeManager

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
        private const val LOG_TAG = "MainApplication"
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

        private suspend fun runBestEffort(what: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warnBestEffortFailed(what, e)
            } catch (e: LinkageError) {
                warnBestEffortFailed(what, e)
            }
        }

        private fun warnBestEffortFailed(what: String, failure: Throwable) {
            try {
                Log.w(LOG_TAG, "$what failed", failure)
            } catch (loggingFailure: RuntimeException) {
            }
        }

        fun createLog(type: String, error: String = "") {
            applicationScope.launch {
                runBestEffort("createLog") {
                    saveLogToRoom(type, error, "${coreDependenciesEntryPoint.timeProvider().now()}")
                }
            }
        }

        suspend fun saveLogToRoom(type: String, error: String, time: String): Boolean {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                CoreDependenciesEntryPoint::class.java
            )
            val diagnosticsRepository = entryPoint.diagnosticsRepository()
            return diagnosticsRepository.saveLogToRoom(type, error, time)
        }

        suspend fun saveLogsToRoom(pendingLogs: List<CrashLogStore.PendingLog>): Boolean {
            if (pendingLogs.isEmpty()) return true
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                CoreDependenciesEntryPoint::class.java
            )
            val diagnosticsRepository = entryPoint.diagnosticsRepository()
            return diagnosticsRepository.saveLogsToRoom(pendingLogs)
        }

        private fun applyThemeMode(themeMode: String?) {
            when (themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        
        private const val REACHABILITY_CACHE_TTL_MS = 30_000L
        private val reachabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

        suspend fun isServerReachable(
            urlString: String,
            ioDispatcher: CoroutineDispatcher = coreDependenciesEntryPoint.dispatcherProvider().io
        ): Boolean {
            if (urlString.isBlank()) return false

            reachabilityCache[urlString]?.let { (reachable, checkedAt) ->
                if (System.currentTimeMillis() - checkedAt < REACHABILITY_CACHE_TTL_MS) {
                    return reachable
                }
            }

            val serverUrlMapper = coreDependenciesEntryPoint.serverUrlMapper()
            val mapping = serverUrlMapper.processUrl(urlString)
            val urlsToTry = mutableListOf(urlString)
            mapping.alternativeUrl?.let { urlsToTry.add(it) }

            var reachable = false
            for (url in urlsToTry) {
                if (tryConnect(url, ioDispatcher)) {
                    reachable = true
                    break
                }
            }
            reachabilityCache[urlString] = reachable to System.currentTimeMillis()
            return reachable
        }

        suspend fun isPrimaryServerReachable(
            urlString: String,
            ioDispatcher: CoroutineDispatcher = coreDependenciesEntryPoint.dispatcherProvider().io
        ): Boolean {
            if (urlString.isBlank()) return false
            return tryConnect(urlString, ioDispatcher)
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
                    getResponseCode(url)
                }
                responseCode in 200..299
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }

        private fun getResponseCode(url: URL): Int {
            TrafficStats.setThreadStatsTag(NETWORK_TRAFFIC_TAG)
            return try {
                val headCode = executeRequest(url, "HEAD")
                if (headCode == HttpURLConnection.HTTP_BAD_METHOD || headCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
                    executeRequest(url, "GET")
                } else {
                    headCode
                }
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }

        private fun executeRequest(url: URL, method: String): Int {
            val connection = url.openConnection() as HttpURLConnection
            return try {
                connection.requestMethod = method
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }

        fun persistCriticalLog(type: String, error: String) {
            val pendingFile = CrashLogStore.save(context, type, error, coreDependenciesEntryPoint.timeProvider())
            applicationScope.launch {
                runBestEffort("persistCriticalLog") {
                    if (saveLogToRoom(type, error, "${coreDependenciesEntryPoint.timeProvider().now()}")) {
                        pendingFile?.delete()
                    }
                }
            }
        }

        fun handleUncaughtException(e: Throwable) {
            e.printStackTrace()
            val error = e.stackTraceToString()
            persistCriticalLog(ApkLog.ERROR_TYPE_CRASH, error)

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }
    }

    private var isFirstLaunch = true
    private lateinit var anrWatchdog: ANRWatchdog

    override fun onCreate() {
        super.onCreate()
        instance = this
        init(sharedPrefManager)
        setupCriticalProperties()
        LocaleUtils.preload(this)
        performDeferredInitialization()
        setupStrictMode()
        registerExceptionHandler()
        setupLifecycleCallbacks()
    }

    private fun performDeferredInitialization() {
        applicationScope.launch(dispatcherProvider.io) {
            runBestEffort("FileUtils.warmUp") { FileUtils.warmUp(this@MainApplication) }
            runBestEffort("SecurePrefs.warmUp") { SecurePrefs.warmUp(this@MainApplication) }
            runBestEffort("MarkdownUtils.warmUp") { MarkdownUtils.warmUp(this@MainApplication) }
            runBestEffort("GifInfoHandle preload") { Class.forName("pl.droidsonroids.gif.GifInfoHandle") }
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
            withContext(dispatcherProvider.io) {
                val pendingLogs = CrashLogStore.loadPendingLogs(this@MainApplication)
                if (pendingLogs.isNotEmpty()) {
                    if (saveLogsToRoom(pendingLogs)) {
                        for (pending in pendingLogs) {
                            pending.file.delete()
                        }
                    }
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

    private suspend fun initializeDatabaseConnection() {
        withContext(dispatcherProvider.io) {
            appDatabase.openHelper.writableDatabase
        }
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
                scope = applicationScope,
                timeout = 5000L,
                listener = object : ANRWatchdog.ANRListener {
                    override fun onAppNotResponding(message: String, blockedThread: Thread, duration: Long) {
                        val error = "ANR detected! Duration: ${duration}ms\n $message"
                        persistCriticalLog(ANR_LOG_TYPE, error)
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
                    ServiceDependenciesEntryPoint::class.java
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
                if (!isConnected) return@onEach

                val serverUrl = sharedPrefManager.getServerUrl()
                if (serverUrl.isEmpty()) return@onEach

                applicationScope.launch {
                    checkServerAndStartDownload(serverUrl)
                }
            }.launchIn(applicationScope)
        }
    }

    private suspend fun checkServerAndStartDownload(serverUrl: String) {
        val canReachServer = isServerReachable(serverUrl, dispatcherProvider.io)
        if (canReachServer && defaultPref.getBoolean("beta_auto_download", false)) {
            resourceDownloadCoordinator.startBackgroundDownload(
                downloadAllFiles(resourcesRepository.getAllLibrariesToSync())
            )
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
        return themeManager.getCurrentThemeMode()
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            PdfThumbnailLoader.evictAll()
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
