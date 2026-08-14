package org.ole.planet.myplanet.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadError
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val BATCH_SIZE = 100

private inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(BATCH_SIZE).forEach(action)
}

@Singleton
class TeamsUploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teamsSyncRepository: Lazy<TeamsSyncRepository>,
    private val uploadCoordinator: UploadCoordinator,
    private val uploadConfigs: UploadConfigs,
    private val uploadRepository: UploadRepository,
    private val apiInterface: ApiInterface,
    private val retryQueue: RetryQueue,
    private val userRepository: UserRepository,
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope scope: CoroutineScope
) : FileUploader(uploadRepository, scope), TeamsUploadRepository {

    override suspend fun uploadResource(listener: OnSuccessListener?) {
        try {
            val user = userRepository.getUserModel()
            val result = uploadCoordinator.uploadRoom(uploadConfigs.getResourcesConfig(user))

            when (result) {
                is UploadResult.Success -> {
                    listener?.let { l ->
                        val libraryIds = result.items.map { it.localId }
                        if (libraryIds.isNotEmpty()) {
                            val libraries = resourcesRepository.getLibraryItemsByIds(libraryIds)
                            val libMap = libraries.associateBy { it.id }

                            result.items.forEach { item ->
                                libMap[item.localId]?.let { library ->
                                    uploadAttachment(item.remoteId, item.remoteRev, library, l)
                                }
                            }
                        }
                    }
                    notifyListener(listener, "Uploaded ${result.items.size} resources successfully")
                }
                is UploadResult.PartialSuccess -> {
                    listener?.let { l ->
                        val libraryIds = result.succeeded.map { it.localId }
                        if (libraryIds.isNotEmpty()) {
                            val libraries = resourcesRepository.getLibraryItemsByIds(libraryIds)
                            val libMap = libraries.associateBy { it.id }

                            result.succeeded.forEach { item ->
                                libMap[item.localId]?.let { library ->
                                    uploadAttachment(item.remoteId, item.remoteRev, library, l)
                                }
                            }
                        }
                    }
                    notifyListener(listener, "Partial success: ${result.succeeded.size} succeeded, ${result.failed.size} failed")
                }
                is UploadResult.Failure -> {
                    notifyListener(listener, "Upload failed: ${result.errors.size} errors")
                }
                is UploadResult.Empty -> {
                    notifyListener(listener, "No resources to upload")
                }
            }
        } catch (e: Exception) {
            Log.e("TeamsUploadRepository", "Resource upload failed", e)
            notifyListener(listener, "Resource upload failed: ${e.message}")
        }
    }

    override suspend fun uploadTeams() {
        val teamsToUpload = teamsSyncRepository.get().getTeamsForUpload()

        withContext(dispatcherProvider.io) {
            teamsToUpload.processInBatches { batch ->
                val deletedIds = mutableListOf<String>()
                val uploadedTeams = mutableMapOf<String, String>()

                val bulkDocs = com.google.gson.JsonArray()
                val teamMap = mutableMapOf<String, org.ole.planet.myplanet.repository.TeamUploadData>()

                batch.forEach { teamData ->
                    teamData.teamId?.let { id ->
                        teamMap[id] = teamData
                    }
                    bulkDocs.add(teamData.serialized)
                }

                if (bulkDocs.size() == 0) return@processInBatches

                val payload = com.google.gson.JsonObject()
                payload.add("docs", bulkDocs)

                try {
                    val response = uploadRepository.postUploadArray(
                        "${UrlUtils.getUrl()}/teams/_bulk_docs", payload
                    )

                    val responseBody = response.body()

                    if (response.isSuccessful && responseBody != null) {
                        for (i in 0 until responseBody.size()) {
                            val element = responseBody.get(i).asJsonObject
                            val id = getString("id", element)
                            val teamData = teamMap[id] ?: continue

                            if (element.has("error")) {
                                // 200 bulk response code prevents retry here, as per doc errors aren't retried
                                queueTeamRetry(teamData, response.code(), if (teamData.isDeletePending) "PUT" else "POST", id)
                            } else {
                                var rev = getString("rev", element)
                                if (teamData.isDeletePending) {
                                    deletedIds.add(id)
                                } else {
                                    if (!teamData.imageName.isNullOrEmpty() && rev.isNotEmpty()) {
                                        rev = uploadTeamImageAttachment(id, rev, teamData.imageName)
                                    }
                                    uploadedTeams[id] = rev
                                }
                            }
                        }

                        if (deletedIds.isNotEmpty()) {
                            teamsSyncRepository.get().deleteLocalTeamRecords(deletedIds)
                        }
                        if (uploadedTeams.isNotEmpty()) {
                            teamsSyncRepository.get().markTeamsUploaded(uploadedTeams)
                        }

                    } else {
                        // Entire bulk failed, queue all
                        batch.forEach { teamData ->
                            queueTeamRetry(teamData, response.code(), if (teamData.isDeletePending) "PUT" else "POST", teamData.teamId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TeamsUploadRepository", "Exception in TeamsUploadRepository bulk upload", e)
                    batch.forEach { teamData ->
                        queueTeamRetry(teamData, null, if (teamData.isDeletePending) "PUT" else "POST", teamData.teamId, e)
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

    override suspend fun uploadTeamActivities() {
        uploadCoordinator.uploadRoom(uploadConfigs.TeamActivities)
    }

    private suspend fun queueTeamRetry(
        teamData: org.ole.planet.myplanet.repository.TeamUploadData,
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
            modelClassName = "Team"
        )
    }

    private suspend fun uploadTeamImageAttachment(teamId: String, rev: String, imageName: String): String {
        val imageFile = MyTeam.getAttachmentFile(context, teamId, imageName) ?: return rev
        if (!imageFile.exists()) return rev
        return try {
            val mimeType = FileUtils.getMimeType(imageName) ?: "image/*"
            val body = imageFile.readBytes().toRequestBody(mimeType.toMediaTypeOrNull())
            val encodedName = Uri.encode(imageName)
            val url = "${UrlUtils.getUrl()}/teams/$teamId/$encodedName"
            val response = apiInterface.uploadResource(FileUploader.getHeaderMap(mimeType, rev), url, body)
            val newRev = response.body()?.get("rev")?.asString
            if (!newRev.isNullOrEmpty()) {
                newRev
            } else {
                rev
            }
        } catch (e: Exception) {
            Log.e("TeamsUploadRepository", "Failed to upload team image attachment", e)
            rev
        }
    }

    private suspend fun notifyListener(listener: OnSuccessListener?, message: String) {
        withContext(dispatcherProvider.mainImmediate) {
            listener?.onSuccess(message)
        }
    }
}
