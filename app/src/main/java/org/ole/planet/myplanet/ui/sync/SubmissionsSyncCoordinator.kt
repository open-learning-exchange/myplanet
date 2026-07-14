package org.ole.planet.myplanet.ui.sync

import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.SubmissionUploadExecutor

@Singleton
class SubmissionsSyncCoordinator @Inject constructor(
    private val uploadManager: UploadManager,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val submissionUploadExecutor: SubmissionUploadExecutor
) {
    fun checkAvailableServer(syncStartTime: Long) {
        Log.d("SubmissionsSyncCoordinator", "checkAvailableServer started, syncStartTime: $syncStartTime")
        val updateUrl = sharedPrefManager.getServerUrl()
        Log.d("SubmissionsSyncCoordinator", "Server URL: $updateUrl")
        val mapping = serverUrlMapper.processUrl(updateUrl)

        submissionUploadExecutor.execute {
            Log.d("SubmissionsSyncCoordinator", "ApplicationScope coroutine started, will not be cancelled by fragment lifecycle")
            Log.d("SubmissionsSyncCoordinator", "Starting server reachability checks (15s timeout each)")
            val checkStartTime = SystemClock.elapsedRealtime()

            val primaryCheck = async {
                try {
                    Log.d("SubmissionsSyncCoordinator", "Checking primary URL: ${mapping.primaryUrl}")
                    val result = withTimeoutOrNull(15000) {
                        MainApplication.isServerReachable(mapping.primaryUrl)
                    } ?: false
                    Log.d("SubmissionsSyncCoordinator", "Primary check result: $result")
                    result
                } catch (e: Exception) {
                    Log.e("SubmissionsSyncCoordinator", "Primary check failed", e)
                    false
                }
            }

            val alternativeCheck = async {
                try {
                    Log.d("SubmissionsSyncCoordinator", "Checking alternative URL: ${mapping.alternativeUrl}")
                    val result = withTimeoutOrNull(15000) {
                        mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true
                    } ?: false
                    Log.d("SubmissionsSyncCoordinator", "Alternative check result: $result")
                    result
                } catch (e: Exception) {
                    Log.e("SubmissionsSyncCoordinator", "Alternative check failed", e)
                    false
                }
            }

            val primaryAvailable = primaryCheck.await()
            val alternativeAvailable = alternativeCheck.await()
            val checkDuration = SystemClock.elapsedRealtime() - checkStartTime
            Log.d("SubmissionsSyncCoordinator", "Server checks completed in ${checkDuration}ms. Primary: $primaryAvailable, Alternative: $alternativeAvailable")

            if (primaryAvailable || alternativeAvailable) {
                Log.d("SubmissionsSyncCoordinator", "Server is reachable, proceeding with upload")
                if (!primaryAvailable) {
                    mapping.alternativeUrl?.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        val editor = sharedPrefManager.rawPreferences.edit()
                        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, sharedPrefManager.rawPreferences)
                    }
                }
                uploadSubmissionsWithTiming(syncStartTime)
            } else {
                Log.w("SubmissionsSyncCoordinator", "No server reachable, upload skipped. Total time since button click: ${SystemClock.elapsedRealtime() - syncStartTime}ms")
            }
        }
    }

    private suspend fun uploadSubmissionsWithTiming(syncStartTime: Long) {
        try {
            Log.d("SubmissionsSyncCoordinator", "About to call uploadSubmissions with syncStartTime: $syncStartTime")
            uploadManager.uploadAdoptedSurveys()
            uploadManager.uploadSubmissions(syncStartTime)
        } catch (e: Exception) {
            Log.e("SubmissionsSyncCoordinator", "Error during upload", e)
            e.printStackTrace()
        }
    }
}
