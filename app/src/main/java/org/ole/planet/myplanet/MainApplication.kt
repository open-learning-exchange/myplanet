package org.ole.planet.myplanet

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.Settings
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.ole.planet.myplanet.callback.TeamPageListener
import org.ole.planet.myplanet.service.AutoSyncWorker
import org.ole.planet.myplanet.service.StayOnlineWorker
import org.ole.planet.myplanet.service.TaskNotificationWorker
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.Utilities
import java.util.concurrent.TimeUnit

class MainApplication : Application() {
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
    }
    override fun onCreate() {
        super.onCreate()
        context = this
        preferences = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)

        // Initialize libraries and settings
        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        builder.detectFileUriExposure()

        // Set up auto-sync using WorkManager
        if (preferences?.getBoolean("autoSync", false) == true && preferences?.contains("autoSyncInterval")!!) {
            val syncInterval = preferences?.getInt("autoSyncInterval", 60 * 60)
            scheduleAutoSyncWork(syncInterval)
        } else {
            cancelAutoSyncWork()
        }

        // Set up other periodic works using WorkManager
        scheduleStayOnlineWork()
        scheduleTaskNotificationWork()
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
}