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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import kotlin.coroutines.resume
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.datamanager.Service.CheckVersionCallback
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities

class AutoSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams), SyncListener, CheckVersionCallback, SuccessListener {
    private lateinit var preferences: SharedPreferences
    private lateinit var syncManager: SyncManager
    private lateinit var uploadManager: UploadManager
    private lateinit var uploadToShelfService: UploadToShelfService
    private val workerScope = CoroutineScope(Dispatchers.IO)
    override fun doWork(): Result {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val entryPoint = EntryPointAccessors.fromApplication(context, AutoSyncEntryPoint::class.java)
        syncManager = entryPoint.syncManager()
        uploadManager = entryPoint.uploadManager()
        uploadToShelfService = entryPoint.uploadToShelfService()
        val lastSync = preferences.getLong("LastSync", 0)
        val currentTime = System.currentTimeMillis()
        val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
        if (currentTime - lastSync > syncInterval * 1000) {
            workerScope.launch {
                if (isAppInForeground(context)) {
                    withContext(Dispatchers.Main) {
                        Utilities.toast(context, "Syncing started...")
                    }
                }
                Service(context).checkVersion(this@AutoSyncWorker, preferences)
            }
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
        startDownloadUpdate(context, UrlUtils.getApkUpdateUrl(info?.localapkpath), null)
    }

    override fun onCheckingVersion() {}
    override fun onError(msg: String, blockSync: Boolean) {
        if (!blockSync) {
            workerScope.launch(Dispatchers.IO) {
                try {
                    Log.d("AutoSyncWorker", "Starting onError sync process.")
                    syncManager.start(this@AutoSyncWorker, "upload")
                    uploadToShelfService.uploadUserDataSuspend()
                    Service(MainApplication.context).healthAccessSuspend()
                    uploadToShelfService.uploadHealth()

                    if (!MainApplication.isSyncRunning) {
                        MainApplication.isSyncRunning = true
                        try {
                            Log.d("AutoSyncWorker", "Starting upload tasks.")
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
                            uploadManager.uploadActivitiesSuspend()
                            MainApplication.isSyncRunning = false
                            Log.d("AutoSyncWorker", "uploadActivities complete.")
                        } catch (e: Exception) {
                            MainApplication.isSyncRunning = false
                            Log.e("AutoSyncWorker", "Upload error: ${e.message}")
                            e.printStackTrace()
                        }
                    } else {
                        Log.d("AutoSyncWorker", "Sync is already running.")
                    }
                } catch (e: Exception) {
                    Log.e("AutoSyncWorker", "onError error: ${e.message}")
                    e.printStackTrace()
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

suspend fun UploadToShelfService.uploadUserDataSuspend() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
    uploadUserData {
        continuation.resume(Unit)
    }
}

suspend fun Service.healthAccessSuspend() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
    healthAccess {
        continuation.resume(Unit)
    }
}

suspend fun UploadManager.uploadActivitiesSuspend() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
    uploadActivities {
        continuation.resume(Unit)
    }
}
