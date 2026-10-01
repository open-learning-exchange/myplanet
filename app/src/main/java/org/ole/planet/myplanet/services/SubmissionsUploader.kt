package org.ole.planet.myplanet.services

import androidx.core.net.toUri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.AppLog
import org.ole.planet.myplanet.utils.ServerReachabilityProvider
import org.ole.planet.myplanet.utils.TimeProvider

@Singleton
class SubmissionsUploader @Inject constructor(
    private val uploadManager: UploadManager,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val submissionUploadExecutor: SubmissionUploadExecutor,
    private val serverReachabilityProvider: ServerReachabilityProvider,
    private val timeProvider: TimeProvider
) {
    fun checkAvailableServer(syncStartTime: Long) {
        AppLog.d("SubmissionsUploader", "checkAvailableServer started, syncStartTime: $syncStartTime")
        val updateUrl = sharedPrefManager.getServerUrl()
        AppLog.d("SubmissionsUploader", "Server URL: $updateUrl")
        val mapping = serverUrlMapper.processUrl(updateUrl)

        submissionUploadExecutor.execute {
            AppLog.d("SubmissionsUploader", "ApplicationScope coroutine started, will not be cancelled by fragment lifecycle")
            AppLog.d("SubmissionsUploader", "Starting server reachability checks (15s timeout each)")
            val checkStartTime = timeProvider.elapsedRealtime()

            val primaryCheck = async {
                try {
                    AppLog.d("SubmissionsUploader", "Checking primary URL: ${mapping.primaryUrl}")
                    val result = withTimeoutOrNull(15000) {
                        serverReachabilityProvider.isServerReachable(mapping.primaryUrl)
                    } ?: false
                    AppLog.d("SubmissionsUploader", "Primary check result: $result")
                    result
                } catch (e: Exception) {
                    AppLog.e("SubmissionsUploader", "Primary check failed", e)
                    false
                }
            }

            val alternativeCheck = async {
                try {
                    AppLog.d("SubmissionsUploader", "Checking alternative URL: ${mapping.alternativeUrl}")
                    val result = withTimeoutOrNull(15000) {
                        mapping.alternativeUrl?.let { serverReachabilityProvider.isServerReachable(it) } == true
                    } ?: false
                    AppLog.d("SubmissionsUploader", "Alternative check result: $result")
                    result
                } catch (e: Exception) {
                    AppLog.e("SubmissionsUploader", "Alternative check failed", e)
                    false
                }
            }

            val primaryAvailable = primaryCheck.await()
            val alternativeAvailable = alternativeCheck.await()
            val checkDuration = timeProvider.elapsedRealtime() - checkStartTime
            AppLog.d("SubmissionsUploader", "Server checks completed in ${checkDuration}ms. Primary: $primaryAvailable, Alternative: $alternativeAvailable")

            if (primaryAvailable || alternativeAvailable) {
                AppLog.d("SubmissionsUploader", "Server is reachable, proceeding with upload")
                if (!primaryAvailable) {
                    mapping.alternativeUrl?.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        serverUrlMapper.updateUrlPreferences(sharedPrefManager, uri, alternativeUrl, mapping.primaryUrl)
                    }
                }
                uploadSubmissionsWithTiming(syncStartTime)
            } else {
                AppLog.w("SubmissionsUploader", "No server reachable, upload skipped. Total time since button click: ${timeProvider.elapsedRealtime() - syncStartTime}ms")
            }
        }
    }

    private suspend fun uploadSubmissionsWithTiming(syncStartTime: Long) {
        try {
            AppLog.d("SubmissionsUploader", "About to call uploadSubmissions with syncStartTime: $syncStartTime")
            uploadManager.uploadAdoptedSurveys()
            uploadManager.uploadSubmissions(syncStartTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "uploadSubmissionsWithTiming failed", e)
        }
    }

    companion object {
        private const val TAG = "SubmissionsUploader"
    }
}
