package org.ole.planet.myplanet.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ConfigurationsRepository.CheckVersionCallback
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sharedPrefManager: SharedPrefManager,
    private val syncManager: SyncManager,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService,
    private val configurationsRepository: ConfigurationsRepository,
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(context, workerParams), OnSyncListener, CheckVersionCallback, OnSuccessListener {

    private lateinit var workerScope: CoroutineScope
    private var syncContinuation: CancellableContinuation<Unit>? = null

    override suspend fun doWork(): Result = coroutineScope {
        if (isStopped) return@coroutineScope Result.success()
        workerScope = this

        val currentTime = System.currentTimeMillis()
        val lastSync = sharedPrefManager.getLastSync()
        val syncInterval = sharedPrefManager.getAutoSyncInterval()
        if (currentTime - lastSync > syncInterval * 1000) {
            val serverReachable = configurationsRepository.checkServerAvailability()
            if (!serverReachable) {
                return@coroutineScope Result.success()
            }
            if (isAppInForeground(context)) {
                withContext(dispatcherProvider.main) {
                    Utilities.toast(context, "Syncing started...")
                }
            }
            suspendCancellableCoroutine { continuation ->
                syncContinuation = continuation
                configurationsRepository.checkVersion(this@AutoSyncWorker, sharedPrefManager)
            }
        }
        return@coroutineScope Result.success()
    }

    override fun onSyncStarted() {}

    override fun onSyncComplete() {}

    override fun onSyncFailed(msg: String?) {
        syncContinuation?.takeIf { it.isActive }?.resume(Unit)
        syncContinuation = null
        if (MainApplication.syncFailedCount > 3) {
            context.startActivity(
                Intent(context, LoginActivity::class.java)
                    .putExtra("showWifiDialog", true)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        workerScope.launch(dispatcherProvider.main) {
            startDownloadUpdate(context, UrlUtils.getApkUpdateUrl(info?.localapkpath), null, workerScope, configurationsRepository)
        }
        syncContinuation?.takeIf { it.isActive }?.resume(Unit)
        syncContinuation = null
    }

    override fun onCheckingVersion() {}

    override fun onError(msg: String, blockSync: Boolean) {
        if (blockSync) {
            syncContinuation?.takeIf { it.isActive }?.resume(Unit)
            syncContinuation = null
            return
        }
        workerScope.launch(dispatcherProvider.io) {
            syncManager.start(this@AutoSyncWorker, "upload")
            launch {
                suspendCancellableCoroutine { cont ->
                    uploadToShelfService.uploadUserData {
                        workerScope.launch {
                            val status = configurationsRepository.checkHealth()
                            Log.d("AutoSyncWorker", "Health check completed with status: $status")
                            uploadToShelfService.uploadHealth()
                        }
                        cont.resume(Unit)
                    }
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
                    Log.e("AutoSyncWorker", "error: ${e.message}")
                    withContext(dispatcherProvider.main) {
                        onSyncFailed(e.message)
                    }
                } finally {
                    MainApplication.isSyncRunning.set(false)
                }
            }
        }
        syncContinuation?.takeIf { it.isActive }?.resume(Unit)
        syncContinuation = null
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
