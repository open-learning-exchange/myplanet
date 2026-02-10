package org.ole.planet.myplanet.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DataService
import org.ole.planet.myplanet.repository.ConfigurationsRepository.CheckVersionCallback
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    @param:AppPreferences private val preferences: SharedPreferences,
    private val syncManager: SyncManager,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService,
    private val configurationsRepository: ConfigurationsRepository
) : CoroutineWorker(context, workerParams), OnSyncListener, CheckVersionCallback, OnSuccessListener {

    override suspend fun doWork(): Result {
        if (isStopped) return Result.success()

        val lastSync = preferences.getLong("LastSync", 0)
        val currentTime = System.currentTimeMillis()
        val syncInterval = preferences.getInt("autoSyncInterval", 60 * 60)
        if (currentTime - lastSync > syncInterval * 1000) {
            if (isAppInForeground(context)) {
                withContext(Dispatchers.Main) {
                    Utilities.toast(context, "Syncing started...")
                }
            }
            configurationsRepository.checkVersion(this, preferences)
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
        MainApplication.applicationScope.let { scope ->
            startDownloadUpdate(context, UrlUtils.getApkUpdateUrl(info?.localapkpath), null, scope, configurationsRepository)
        }
    }

    override fun onCheckingVersion() {}

    override fun onError(msg: String, blockSync: Boolean) {
        if (!blockSync) {
            syncManager.start(this, "upload")
            uploadToShelfService.uploadUserData {
                configurationsRepository.checkHealth {
                    uploadToShelfService.uploadHealth()
                }
            }
            if (!MainApplication.isSyncRunning) {
                MainApplication.isSyncRunning = true
                MainApplication.applicationScope.let { scope ->
                    scope.launch {
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
                            throw e
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
