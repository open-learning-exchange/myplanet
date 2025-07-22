package org.ole.planet.myplanet.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Date
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.datamanager.Service.CheckVersionCallback
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utilities.Utilities

class AutoSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadManager: UploadManager,
    private val syncManager: SyncManager
) : Worker(context, workerParams), SyncListener, CheckVersionCallback, SuccessListener {
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): AutoSyncWorker
    }
    
    private lateinit var preferences: SharedPreferences
    override fun doWork(): Result {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSync = preferences.getLong("LastSync", 0)
        val currentTime = System.currentTimeMillis()
        val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
        if (currentTime - lastSync > syncInterval * 1000) {
            if (isAppInForeground(context)) {
                Utilities.toast(context, "Syncing started...")
            }
            Service(context).checkVersion(this, preferences)
        }
        return Result.success()
    }

    override fun onSyncStarted() {}

    override fun onSyncComplete() {}

    override fun onSyncFailed(msg: String?) {
        if (MainApplication.syncFailedCount > 3) {
            context.startActivity(Intent(context, LoginActivity::class.java)
                .putExtra("showWifiDialog", true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        startDownloadUpdate(context, Utilities.getApkUpdateUrl(info?.localapkpath), null)
    }

    override fun onCheckingVersion() {}
    override fun onError(msg: String, blockSync: Boolean) {
        if (!blockSync) {
            syncManager.start(this, "upload")
            UploadToShelfService.instance?.uploadUserData {
                Service(MainApplication.context).healthAccess {
                    UploadToShelfService.instance?.uploadHealth()
                }
            }
            if (!MainApplication.isSyncRunning) {
                MainApplication.isSyncRunning = true
                uploadManager.uploadExamResult(this)
                uploadManager.uploadFeedback(this)
                uploadManager.uploadAchievement()
                uploadManager.uploadResourceActivities("")
                uploadManager.uploadUserActivities(this)
                uploadManager.uploadCourseActivities()
                uploadManager.uploadSearchActivity()
                uploadManager.uploadRating()
                uploadManager.uploadResource(this)
                uploadManager.uploadNews()
                uploadManager.uploadTeams()
                uploadManager.uploadTeamTask()
                uploadManager.uploadMeetups()
                uploadManager.uploadCrashLog()
                uploadManager.uploadSubmissions()
                uploadManager.uploadActivities { MainApplication.isSyncRunning = false }
            }
        }
    }

    override fun onSuccess(success: String?) {
        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        settings.edit { putLong("lastUsageUploaded", Date().time) }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false

        for (processInfo in runningProcesses) {
            if (processInfo.processName == context.packageName &&
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true
            }
        }
        return false
    }
}
