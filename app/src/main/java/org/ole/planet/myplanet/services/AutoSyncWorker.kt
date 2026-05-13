package org.ole.planet.myplanet.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ConfigurationsRepository.CheckVersionCallback
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import java.util.Date
import java.util.concurrent.CancellationException

@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sharedPrefManager: SharedPrefManager,
    private val syncManager: SyncManager,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService,
    private val configurationsRepository: ConfigurationsRepository
) : CoroutineWorker(context, workerParams), OnSyncListener, CheckVersionCallback, OnSuccessListener {

    private val workComplete = CompletableDeferred<Unit>()

    override suspend fun doWork(): Result {
        if (isStopped) return Result.success()

        val currentTime = System.currentTimeMillis()
        val lastSync = sharedPrefManager.getLastSync()
        val syncInterval = sharedPrefManager.getAutoSyncInterval()
        val elapsed = currentTime - lastSync
        if (elapsed <= syncInterval * 1000L) {
            return Result.success()
        }

        val serverReachable = configurationsRepository.checkServerAvailability()
        if (!serverReachable) {
            return Result.success()
        }

        if (isAppInForeground(context)) {
            withContext(Dispatchers.Main) { Utilities.toast(context, "Syncing started...") }
        }
        configurationsRepository.checkVersion(this, sharedPrefManager)
        withTimeoutOrNull(10_000L) { workComplete.await() }
        return Result.success()
    }

    override fun onSyncStarted() {}

    override fun onSyncComplete() {}

    override fun onSyncFailed(msg: String?) {
        workComplete.complete(Unit)
        if (MainApplication.syncFailedCount > 3) {
            context.startActivity(
                Intent(context, LoginActivity::class.java)
                    .putExtra("showWifiDialog", true)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        workComplete.complete(Unit)
        MainApplication.applicationScope.launch(Dispatchers.Main) {
            startDownloadUpdate(context, UrlUtils.getApkUpdateUrl(info?.localapkpath), null, MainApplication.applicationScope, configurationsRepository)
        }
    }

    override fun onCheckingVersion() {}

    override fun onError(msg: String, blockSync: Boolean) {
        if (blockSync) {
            workComplete.complete(Unit)
            return
        }
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                syncManager.start(this@AutoSyncWorker, "upload")
                uploadToShelfService.uploadUserData {
                    MainApplication.applicationScope.launch {
                        uploadToShelfService.uploadHealth()
                    }
                }
                if (MainApplication.isSyncRunning.compareAndSet(false, true)) {
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
                        sharedPrefManager.setLastSync(Date().time)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onSyncFailed(e.message)
                    } finally {
                        MainApplication.isSyncRunning.set(false)
                    }
                }
            } finally {
                workComplete.complete(Unit)
            }
        }
    }

    override fun onSuccess(success: String?) {
        sharedPrefManager.setLastUsageUploaded(Date().time)
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        return runningProcesses.any {
            it.processName == context.packageName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
