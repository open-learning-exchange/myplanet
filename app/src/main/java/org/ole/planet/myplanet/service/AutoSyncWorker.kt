package org.ole.planet.myplanet.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationRepository
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities

class AutoSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams), SyncListener, ConfigurationRepository.CheckVersionCallback,
    SuccessListener {
    private lateinit var preferences: SharedPreferences
    private lateinit var syncManager: SyncManager
    private lateinit var uploadManager: UploadManager
    private lateinit var uploadToShelfService: UploadToShelfService
    private lateinit var configurationRepository: ConfigurationRepository
    private val workerScope = CoroutineScope(Dispatchers.IO)
    override fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(context, AutoSyncEntryPoint::class.java)
        preferences = entryPoint.sharedPreferences()
        syncManager = entryPoint.syncManager()
        uploadManager = entryPoint.uploadManager()
        uploadToShelfService = entryPoint.uploadToShelfService()
        configurationRepository = entryPoint.configurationRepository()
        val lastSync = preferences.getLong("LastSync", 0)
        val currentTime = System.currentTimeMillis()
        val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
        if (currentTime - lastSync > syncInterval * 1000) {
            if (isAppInForeground(context)) {
                Utilities.toast(context, "Syncing started...")
            }
            configurationRepository.checkVersion(this, preferences)
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
        startDownloadUpdate(context, UrlUtils.getApkUpdateUrl(info?.localapkpath), null, workerScope, configurationRepository)
    }

    override fun onCheckingVersion() {}
    override fun onError(msg: String, blockSync: Boolean) {
        if (!blockSync) {
            syncManager.start(this, "upload")
            uploadToShelfService.uploadUserData {
                configurationRepository.checkHealth {
                    uploadToShelfService.uploadHealth()
                }
            }
            if (!MainApplication.isSyncRunning) {
                MainApplication.isSyncRunning = true
                workerScope.launch {
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
                    uploadManager.uploadActivities { MainApplication.isSyncRunning = false }
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
