package org.ole.planet.myplanet.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.util.Date
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DataService
import org.ole.planet.myplanet.data.DataService.CheckVersionCallback
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities

class AutoSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams), OnSyncListener, CheckVersionCallback, OnSuccessListener {
    private lateinit var preferences: SharedPreferences
    private lateinit var syncManager: SyncManager
    private lateinit var uploadManager: UploadManager
    private lateinit var uploadToShelfService: UploadToShelfService
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override fun doWork(): Result {
        if (isStopped) return Result.success()
        val entryPoint = EntryPointAccessors.fromApplication(context, AutoSyncEntryPoint::class.java)
        preferences = entryPoint.sharedPreferences()
        syncManager = entryPoint.syncManager()
        uploadManager = entryPoint.uploadManager()
        uploadToShelfService = entryPoint.uploadToShelfService()
        val lastSync = preferences.getLong("LastSync", 0)
        val currentTime = System.currentTimeMillis()
        val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
        if (currentTime - lastSync > syncInterval * 1000) {
            if (isAppInForeground(context)) {
                Utilities.toast(context, "Syncing started...")
            }
            DataService(context).checkVersion(this, preferences)
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
        startDownloadUpdate(context, UrlUtils.getApkUpdateUrl(info?.localapkpath), null, workerScope)
    }

    override fun onCheckingVersion() {}
    override fun onError(msg: String, blockSync: Boolean) {
        if (!blockSync) {
            syncManager.start(this, "upload")
            uploadToShelfService.uploadUserData {
                DataService(MainApplication.context).healthAccess {
                    uploadToShelfService.uploadHealth()
                }
            }
            if (!MainApplication.isSyncRunning) {
                MainApplication.isSyncRunning = true
                workerScope.launch {
                    try {
                        uploadManager.uploadExamResult(this@AutoSyncWorker)
                        uploadManager.uploadFeedback()
                        uploadManager.uploadAchievement()
                        uploadManager.uploadResourceActivities("")
                        uploadManager.uploadUserActivities(this@AutoSyncWorker)
                        uploadManager.uploadCourseActivities()
                        uploadManager.uploadSearchActivity()
                        uploadManager.uploadRating()
                        uploadManager.uploadResource(this@AutoSyncWorker)
                        uploadManager.uploadNews()
                        uploadManager.uploadTeams()
                        uploadManager.uploadTeamTask()
                        uploadManager.uploadMeetups()
                        uploadManager.uploadAdoptedSurveys()
                        uploadManager.uploadCrashLog()
                        uploadManager.uploadSubmissions()
                        uploadManager.uploadActivities(null)
                    } catch (e: CancellationException) {
                        // Worker was stopped - this is expected, don't treat as failure
                        Log.d("AutoSyncWorker", "Upload cancelled - worker stopped")
                    } catch (e: Exception) {
                        Log.e("AutoSyncWorker", "error: ${e.message}")
                        onSyncFailed(e.message)
                    } finally {
                        MainApplication.isSyncRunning = false
                    }
                }
            }
        }
    }

    override fun onSuccess(success: String?) {
        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        settings.edit { putLong("lastUsageUploaded", Date().time) }
    }

    override fun onStopped() {
        super.onStopped()
        workerScope.cancel()
        MainApplication.isSyncRunning = false
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
