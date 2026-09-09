package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamUploadData
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils

private inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(BATCH_SIZE).forEach(action)
}

class TeamsUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val teamsSyncRepository: Lazy<TeamsSyncRepository>,
    private val uploadRepository: UploadRepository,
    private val retryQueue: RetryQueue,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun uploadTeams() {
        val teamsToUpload = teamsSyncRepository.get().getTeamsForUpload()

        withContext(dispatcherProvider.io) {
            teamsToUpload.processInBatches { batch ->
                val deletedIds = mutableListOf<String>()
                val uploadedTeams = mutableMapOf<String, String>()

                BulkDocUploader.upload(uploadRepository, "${UrlUtils.getUrl()}/teams/_bulk_docs", batch.map { it to it.serialized }) { teamData, outcome ->
                    when (outcome) {
                        is BulkDocUploader.Outcome.Accepted -> {
                            val id = getString("id", outcome.element)
                            var rev = getString("rev", outcome.element)
                            if (teamData.isDeletePending) {
                                deletedIds.add(id)
                            } else {
                                if (!teamData.imageName.isNullOrEmpty() && rev.isNotEmpty()) {
                                    rev = uploadTeamImageAttachment(id, rev, teamData.imageName)
                                }
                                uploadedTeams[teamData.teamId ?: id] = rev
                            }
                        }
                        is BulkDocUploader.Outcome.Rejected -> {
                            val id = getString("id", outcome.element)
                            queueTeamRetry(teamData, outcome.httpCode, if (teamData.isDeletePending) "PUT" else "POST", id)
                        }
                        is BulkDocUploader.Outcome.RequestFailed -> {
                            queueTeamRetry(teamData, outcome.httpCode, if (teamData.isDeletePending) "PUT" else "POST", teamData.teamId, outcome.exception)
                        }
                    }
                }

                if (deletedIds.isNotEmpty()) {
                    teamsSyncRepository.get().deleteLocalTeamRecords(deletedIds)
                }
                if (uploadedTeams.isNotEmpty()) {
                    teamsSyncRepository.get().markTeamsUploaded(uploadedTeams)
                }
            }
        }
    }

    private suspend fun queueTeamRetry(
        teamData: TeamUploadData,
        httpCode: Int?,
        httpMethod: String,
        dbId: String?,
        exception: Exception? = null
    ) {
        val retryable = exception != null || (httpCode != null && httpCode >= 500)
        if (!retryable) return
        retryQueue.queueFailedOperation(
            uploadType = "MyTeam",
            error = UploadError(
                itemId = teamData.teamId ?: "",
                exception = exception ?: Exception("Upload failed: HTTP $httpCode"),
                retryable = true,
                httpCode = httpCode
            ),
            payload = teamData.serialized,
            endpoint = "teams",
            httpMethod = httpMethod,
            dbId = dbId,
            modelClassName = "MyTeam"
        )
    }

    private suspend fun uploadTeamImageAttachment(teamId: String, rev: String, imageName: String): String {
        val imageFile = MyTeam
            .getAttachmentFile(context, teamId, imageName) ?: return rev
        if (!imageFile.exists()) return rev
        return try {
            val mimeType = FileUtils.getMimeType(imageName) ?: "image/*"
            val body = imageFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val encodedName = Uri.encode(imageName)
            val url = "${UrlUtils.getUrl()}/teams/$teamId/$encodedName"
            val response = uploadRepository.uploadResource(FileUploader.getHeaderMap(mimeType, rev), url, body)
            val newRev = response.body()?.get("rev")?.asString
            if (!newRev.isNullOrEmpty()) {
                newRev
            } else {
                rev
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload team image attachment", e)
            rev
        }
    }

    companion object {
        private const val TAG = "UploadManager"
    }
}
