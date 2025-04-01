package org.ole.planet.myplanet.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.datamanager.Service.CheckVersionCallback
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import androidx.core.content.edit

class AutoSyncWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams), SyncListener, CheckVersionCallback, SuccessListener {
    private lateinit var preferences: SharedPreferences
    override fun doWork(): Result {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSync = preferences.getLong("LastSync", 0)
        val currentTime = System.currentTimeMillis()
        val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
        if (currentTime - lastSync > syncInterval * 1000) {
            // Post a Runnable to the main thread's Handler to show the Toast
            if (isAppInForeground(context)) {
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post {
                    Utilities.toast(
                        context, "Syncing started..."
                    )
                }
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
            SyncManager.instance?.start(this, "upload")
            UploadToShelfService.instance?.uploadUserData {
                Service(MainApplication.context).healthAccess {
                    UploadToShelfService.instance?.uploadHealth()
                }
            }
            if (!MainApplication.isSyncRunning) {
                MainApplication.isSyncRunning = true
                UploadManager.instance?.uploadExamResult(this)
                UploadManager.instance?.uploadFeedback(this)
                UploadManager.instance?.uploadAchievement()
                UploadManager.instance?.uploadResourceActivities("")
                UploadManager.instance?.uploadUserActivities(this)
                UploadManager.instance?.uploadCourseActivities()
                UploadManager.instance?.uploadSearchActivity()
                UploadManager.instance?.uploadRating()
                UploadManager.instance?.uploadResource(this)
                UploadManager.instance?.uploadNews()
                UploadManager.instance?.uploadTeams()
                UploadManager.instance?.uploadTeamTask()
                UploadManager.instance?.uploadMeetups()
                UploadManager.instance?.uploadCrashLog()
                UploadManager.instance?.uploadSubmissions()
                UploadManager.instance?.uploadActivities { MainApplication.isSyncRunning = false }
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
