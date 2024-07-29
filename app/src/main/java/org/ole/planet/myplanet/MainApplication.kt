package org.ole.planet.myplanet

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ole.planet.myplanet.callback.TeamPageListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.service.AutoSyncWorker
import org.ole.planet.myplanet.service.StayOnlineWorker
import org.ole.planet.myplanet.service.TaskNotificationWorker
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.NetworkUtils.initialize
import org.ole.planet.myplanet.utilities.NetworkUtils.startListenNetworkState
import org.ole.planet.myplanet.utilities.NotificationUtil.cancelAll
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils.getVersionName
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        private const val AUTO_SYNC_WORK_TAG = "autoSyncWork"
        private const val STAY_ONLINE_WORK_TAG = "stayOnlineWork"
        private const val TASK_NOTIFICATION_WORK_TAG = "taskNotificationWork"
        lateinit var context: Context
        var preferences: SharedPreferences? = null
        @JvmField
        var syncFailedCount = 0
        @JvmField
        var isCollectionSwitchOn = false
        @JvmField
        var showDownload = false
        @JvmField
        var isSyncRunning = false
        var showHealthDialog = true
        @JvmField
        var listener: TeamPageListener? = null
        val androidId: String
            get() {
                try {
                    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return "0"
            }

        fun createLog(type: String) {
            val service = DatabaseService(context)
            val mRealm = service.realmInstance
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            val log = mRealm.createObject(RealmApkLog::class.java, "${UUID.randomUUID()}")
            val model = UserProfileDbHandler(context).userModel
            if (model != null) {
                log.parentCode = model.parentCode
                log.createdOn = model.planetCode
            }
            log.time = "${Date().time}"
            log.page = ""
            log.version = getVersionName(context)
            log.type = type
            mRealm.commitTransaction()
        }
    }

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    private var isFirstLaunch = true

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate() {
        super.onCreate()
        initialize(CoroutineScope(Dispatchers.IO))

        context = this
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        nightMode()
        // UNCOMMENT BELOW TO FORCE DARK MODE FOR DARK MODE DEVELOPMENT
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        builder.detectFileUriExposure()

        if (preferences?.getBoolean("autoSync", false) == true && preferences?.contains("autoSyncInterval") == true) {
            val syncInterval = preferences?.getInt("autoSyncInterval", 60 * 60)
            scheduleAutoSyncWork(syncInterval)
        } else {
            cancelAutoSyncWork()
        }
        scheduleStayOnlineWork()
        scheduleTaskNotificationWork()

        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, e: Throwable ->
            handleUncaughtException(e)
        }
        registerActivityLifecycleCallbacks(this)
        startListenNetworkState()
        onAppStarted()
    }

    private fun nightMode() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this).getString("dark_mode", "OFF")
        val options = listOf(*resources.getStringArray(R.array.dark_mode_options))
        when (options.indexOf(preference)) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base, "en"))
        Utilities.setContext(base)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.onAttach(this)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            onAppForegrounded()
        }
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            onAppBackgrounded()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        cancelAll(this)
    }

    private fun onAppForegrounded() {
        if (isFirstLaunch) {
            isFirstLaunch = false
        } else {
            val fromForeground = "foreground"
            createLog(fromForeground)
        }
    }
    
    private fun onAppBackgrounded() {}

    private fun onAppStarted() {
        val newStart = "new launch"
        createLog(newStart)
    }

    private fun onAppClosed() {}

    private fun handleUncaughtException(e: Throwable) {
        e.printStackTrace()
        val service = DatabaseService(this)
        val mRealm = service.realmInstance
        if (!mRealm.isInTransaction) {
            mRealm.beginTransaction()
        }
        val log = mRealm.createObject(RealmApkLog::class.java, "${UUID.randomUUID()}")
        val model = UserProfileDbHandler(this).userModel
        if (model != null) {
            log.parentCode = model.parentCode
            log.createdOn = model.planetCode
        }
        log.time = "${Date().time}"
        log.page = ""
        log.version = getVersionName(this)
        log.type = RealmApkLog.ERROR_TYPE_CRASH
        log.setError(e)
        mRealm.commitTransaction()
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
    }

    override fun onTerminate() {
        super.onTerminate()
        onAppClosed()
    }
}
