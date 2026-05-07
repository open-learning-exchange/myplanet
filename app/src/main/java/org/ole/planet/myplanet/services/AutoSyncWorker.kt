package org.ole.planet.myplanet.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ConfigurationsRepository.CheckVersionCallback
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.DialogUtils.startDownloadUpdate
import org.ole.planet.myplanet.utils.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlin.coroutines.resume

@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    @param:AppPreferences private val preferences: SharedPreferences,
    private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
    private val syncManager: SyncManager,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService,
    private val configurationsRepository: ConfigurationsRepository,
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(context, workerParams), OnSyncListener, CheckVersionCallback, OnSuccessListener {

    private lateinit var workerScope: CoroutineScope
    private var syncContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null

    override suspend fun doWork(): Result = coroutineScope {
        if (isStopped) return@coroutineScope Result.success()
        workerScope = this

        val lastSync = sharedPrefManager.getLastSync()
        val currentTime = System.currentTimeMillis()
        val syncInterval = sharedPrefManager.getAutoSyncInterval()
        if (currentTime - lastSync > syncInterval * 1000) {
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
        if (MainApplication.syncFailedCount > 3) {
            context.startActivity(Intent(context, LoginActivity::class.java)
                .putExtra("showWifiDialog", true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        if (!blockSync) {
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

                if (!MainApplication.isSyncRunning) {
                    MainApplication.isSyncRunning = true
                    launch {
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
                            withContext(dispatcherProvider.main) {
                                onSyncFailed(e.message)
                            }
                        } finally {
                            MainApplication.isSyncRunning = false
                        }
                    }
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

        for (processInfo in runningProcesses) {
            if (processInfo.processName == context.packageName &&
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true
            }
        }
        return false
    }
}
