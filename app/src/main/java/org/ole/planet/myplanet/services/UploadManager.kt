package org.ole.planet.myplanet.services

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiClient.client
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils.getAndroidId

private const val BATCH_SIZE = 50

private inline fun <T> Iterable<T>.processInBatches(action: (T) -> Unit) {
    chunked(BATCH_SIZE).forEach { chunk ->
        chunk.forEach { item ->
            action(item)
        }
    }
}

@Singleton
class UploadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    private val submissionsRepository: SubmissionsRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val gson: Gson,
    private val uploadCoordinator: UploadCoordinator,
    private val personalsRepository: PersonalsRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val uploadConfigs: UploadConfigs,
    private val teamsRepository: Lazy<TeamsRepository>,
    private val apiInterface: ApiInterface,
    private val activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
    @ApplicationScope private val scope: CoroutineScope
) : FileUploader(apiInterface, scope) {

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.upload(uploadConfigs.NewsActivities)
    }

    fun uploadActivities(listener: OnSuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)

        scope.launch {
            val model = userRepository.getUserModelSuspending() ?: run {
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Cannot upload activities: user model is null")
                }
                return@launch
            }

            if (model.isManager()) {
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Skipping activities upload for manager")
                }
                return@launch
            }

            try {
                try {
                    apiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/myplanet_activities",
                        MyPlanet.getNormalMyPlanetActivities(MainApplication.context, sharedPrefManager, model)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val response = try {
                    apiInterface.getJsonObject(
                        UrlUtils.header,
                        "${UrlUtils.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}"
                    )
                } catch (e: Exception) {
                    null
                }

                var `object` = response?.body()

                if (`object` != null) {
                    val usages = `object`.getAsJsonArray("usages")
                    usages.addAll(MyPlanet.getTabletUsages(context))
                    `object`.add("usages", usages)
                } else {
                    `object` = MyPlanet.getMyPlanetActivities(context, sharedPrefManager, model)
                }

                try {
                    apiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/myplanet_activities",
                        `object`
                    )
                    withContext(Dispatchers.Main) {
                        listener?.onSuccess("My planet activities uploaded successfully")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        listener?.onSuccess("Failed to upload activities: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Failed to upload activities: ${e.message}")
                }
            }
        }
    }

    suspend fun uploadExamResult(listener: OnSuccessListener) {
        withContext(Dispatchers.IO) {
            try {
                val result = uploadCoordinator.upload(uploadConfigs.ExamResults)

                val message = when (result) {
                    is UploadResult.Success -> "Result sync completed successfully (${result.data} processed, 0 errors)"
                    is UploadResult.PartialSuccess -> "Result sync completed with issues (${result.succeeded.size} processed, ${result.failed.size} errors)"
                    is UploadResult.Failure -> "Result sync failed: ${result.errors.size} errors"
                    is UploadResult.Empty -> "No exam results to upload"
                }

                uploadCourseProgress()
                withContext(Dispatchers.Main) {
                    listener.onSuccess(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during result sync: ${e.message}")
                }
            }
        }
    }

    private fun createImage(user: RealmUser?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", Date().time)
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("private", true)
        user?.id?.let { `object`.addProperty("addedBy", it) }
        user?.parentCode?.let { `object`.addProperty("resideOn", it) }
        user?.planetCode?.let { `object`.addProperty("sourcePlanet", it) }
        val object1 = JsonObject()
        `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    suspend fun uploadAchievement() {
        databaseService.executeTransactionAsync { transactionRealm ->
            val list: List<RealmAchievement> = transactionRealm.where(RealmAchievement::class.java).findAll()
            list.processInBatches { sub ->
                try {
                    if (sub._id?.startsWith("guest") == true) {
                        return@processInBatches
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun uploadCourseProgress() {
        uploadCoordinator.upload(uploadConfigs.CourseProgress)
    }

    suspend fun uploadFeedback(): Boolean {
        return when (val result = uploadCoordinator.upload(uploadConfigs.Feedback)) {
            is UploadResult.Success -> true
            is UploadResult.PartialSuccess -> result.failed.isEmpty()
            is UploadResult.Failure -> false
            is UploadResult.Empty -> true
        }
    }

    suspend fun uploadSubmitPhotos(listener: OnSuccessListener?) {
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)

        val photosToUpload = submissionsRepository.getUnuploadedPhotos()

        if (photosToUpload.isEmpty()) {
            listener?.onSuccess("No photos to upload")
            return
        }

        withContext(Dispatchers.IO) {
            data class UploadedPhotoInfo(val photoId: String, val rev: String, val id: String)

            photosToUpload.chunked(BATCH_SIZE).forEach { batch ->
                val successfulUploads = mutableListOf<UploadedPhotoInfo>()

                batch.forEach { (photoId, serialized) ->
                    try {
                        val `object` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/submissions", serialized
                        ).body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)

                            submissionsRepository.markPhotoUploaded(photoId, rev, id)

                            if (listener != null && photoId != null) {
                                successfulUploads.add(UploadedPhotoInfo(photoId, rev, id))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (listener != null && successfulUploads.isNotEmpty()) {
                    val photoIds = successfulUploads.map { it.photoId }.toTypedArray()
                    val photos = databaseService.withRealm { realm ->
                        val results = realm.where(RealmSubmitPhotos::class.java)
                            .`in`("id", photoIds).findAll()
                        realm.copyFromRealm(results)
                    }

                    photos?.forEach { photo ->
                        val uploadInfo = successfulUploads.find { it.photoId == photo.id }
                        if (uploadInfo != null) {
                            uploadAttachment(uploadInfo.id, uploadInfo.rev, photo, listener)
                        }
                    }
                }
            }
        }
    }

    suspend fun uploadResource(listener: OnSuccessListener?) {
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)

        try {
            data class ResourceData(
                val libraryId: String?,
                val title: String?,
                val isPrivate: Boolean,
                val privateFor: String?,
                val serialized: JsonObject
            )

            val user = userRepository.getUserModelSuspending()

            val resourcesToUpload = databaseService.withRealm { realm ->
                realm.refresh()
                val data = realm.where(RealmMyLibrary::class.java).isNull("_rev").findAll()

                if (data.isEmpty()) {
                    emptyList()
                } else {
                    data.map { library ->
                        ResourceData(
                            libraryId = library.id,
                            title = library.title,
                            isPrivate = library.isPrivate,
                            privateFor = library.privateFor,
                            serialized = RealmMyLibrary.serialize(library, user)
                        )
                    }
                }
            }

            if (resourcesToUpload.isEmpty()) {
                listener?.onSuccess("No resources to upload")
                return
            }

            withContext(Dispatchers.IO) {
                resourcesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                    val successfulUpdates = mutableListOf<Pair<ResourceData, com.google.gson.JsonObject>>()

                    batch.forEach { resourceData ->
                        try {
                            val `object` = apiInterface.postDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/resources", resourceData.serialized
                            ).body()

                            if (`object` != null) {
                                successfulUpdates.add(Pair(resourceData, `object`))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (successfulUpdates.isNotEmpty()) {
                        val libraryIds = successfulUpdates.mapNotNull { it.first.libraryId }.toTypedArray()
                        var isTransactionSuccessful = false

                        try {
                            databaseService.withRealm { transactionRealm ->
                                transactionRealm.executeTransaction { realm ->
                                    val managedLibrariesMap = mutableMapOf<String, RealmMyLibrary>()
                                    if (libraryIds.isNotEmpty()) {
                                        val results = realm.where(RealmMyLibrary::class.java)
                                            .`in`("id", libraryIds)
                                            .findAll()
                                        results.forEach { lib ->
                                            lib.id?.let { id -> managedLibrariesMap[id] = lib }
                                        }
                                    }

                                    successfulUpdates.forEach { (resourceData, `object`) ->
                                        val rev = getString("rev", `object`)
                                        val id = getString("id", `object`)

                                        resourceData.libraryId?.let { libId ->
                                            managedLibrariesMap[libId]?.let { sub ->
                                                sub._rev = rev
                                                sub._id = id
                                            }
                                        }

                                        if (resourceData.isPrivate && !resourceData.privateFor.isNullOrBlank()) {
                                            val planetCode = user?.planetCode?.takeIf { it.isNotBlank() }
                                                ?: sharedPrefManager.getPlanetCode()
                                            val teamResource = realm.createObject(
                                                RealmMyTeam::class.java,
                                                UUID.randomUUID().toString()
                                            )
                                            teamResource.teamId = resourceData.privateFor
                                            teamResource.title = resourceData.title
                                            teamResource.resourceId = id
                                            teamResource.docType = "resourceLink"
                                            teamResource.updated = true
                                            teamResource.teamType = "local"
                                            teamResource.teamPlanetCode = planetCode
                                            teamResource.sourcePlanet = planetCode
                                        }
                                    }
                                }
                            }
                            isTransactionSuccessful = true
                        } catch (e: Exception) {
                            // If the executeTransaction block throws (e.g. disk full, schema conflict),
                            // Realm automatically rolls back the entire transaction.
                            // We catch it here to prevent crashing the batch loop and prevent
                            // `isTransactionSuccessful` from being set to true, so we don't upload
                            // attachments for failed DB writes.
                            e.printStackTrace()
                        }

                        if (isTransactionSuccessful) {
                            listener?.let {
                                try {
                                    val libraries = databaseService.withRealm { realm ->
                                        if (libraryIds.isNotEmpty()) {
                                            val results = realm.where(RealmMyLibrary::class.java)
                                                .`in`("id", libraryIds)
                                                .findAll()
                                            realm.copyFromRealm(results)
                                        } else {
                                            emptyList()
                                        }
                                    }

                                    val libMap = libraries?.associateBy { it.id } ?: emptyMap()

                                    successfulUpdates.forEach { (resourceData, `object`) ->
                                        val rev = getString("rev", `object`)
                                        val id = getString("id", `object`)

                                        resourceData.libraryId?.let { libId ->
                                            libMap[libId]?.let { library ->
                                                uploadAttachment(id, rev, library, listener)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onSuccess("Resource upload failed: ${e.message}")
        }
    }

    suspend fun uploadMyPersonal(personal: RealmMyPersonal): String {
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
            return withContext(Dispatchers.IO) {
                try {
                    val response = apiInterface.postDoc(
                        UrlUtils.header, "application/json",
                        "${UrlUtils.getUrl()}/resources", RealmMyPersonal.serialize(personal, context)
                    )

                    val `object` = response.body()
                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)

                        personal.id?.let { personalId ->
                            personalsRepository.updatePersonalAfterSync(personalId, id, rev)
                        }

                        uploadAttachment(id, rev, personal) { }
                        "Personal resource uploaded successfully"
                    } else {
                        "Failed to upload personal resource: No response"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Unable to upload resource: ${e.message}"
                }
            }
        } else {
            return "Resource already uploaded"
        }
    }

    suspend fun uploadTeamTask() {
        uploadCoordinator.upload(uploadConfigs.TeamTask)
    }

    suspend fun uploadSubmissions(buttonClickTime: Long = 0L) {
        Log.d("UploadManager", "uploadSubmissions called with buttonClickTime: $buttonClickTime")
        val startTime = if (buttonClickTime > 0) buttonClickTime else System.currentTimeMillis()

        if (buttonClickTime > 0) {
            Log.d("UploadManager", "Mini survey sync timer started from button click at: $startTime")
        } else {
            Log.d("UploadManager", "Mini survey sync started at: $startTime (buttonClickTime was $buttonClickTime)")
        }

        try {
            val result = uploadCoordinator.upload(uploadConfigs.Submissions)

            Log.d("UploadManager", when (result) {
                is UploadResult.Success -> "Uploaded ${result.data} submissions successfully"
                is UploadResult.PartialSuccess -> "Partial success: ${result.succeeded.size} succeeded, ${result.failed.size} failed"
                is UploadResult.Failure -> "Upload failed: ${result.errors.size} errors"
                is UploadResult.Empty -> "No submissions to upload"
            })
        } catch (e: Exception) {
            Log.e("UploadManager", "Error uploading submissions", e)
        } finally {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("UploadManager", "Mini survey sync completed at: $endTime")
            Log.d("UploadManager", "Total time from button click to sync completion: ${duration}ms (${duration / 1000.0}s)")
        }
    }

    suspend fun uploadTeams() {
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)

        val teamsToUpload = teamsRepository.get().getTeamsForUpload()

        withContext(Dispatchers.IO) {
            teamsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { teamData ->
                    try {
                        val response = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/teams", teamData.serialized
                        )

                        val `object` = response.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            teamsRepository.get().markTeamUploaded(teamData.teamId, rev)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun uploadUserActivities(listener: OnSuccessListener) {
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)
        val model = userRepository.getUserModelSuspending() ?: run {
            withContext(Dispatchers.Main) {
                listener.onSuccess("Cannot upload user activities: user model is null")
            }
            return
        }

        if (model.isManager()) {
            withContext(Dispatchers.Main) {
                listener.onSuccess("Skipping user activities upload for manager")
            }
            return
        }

        try {
            val activitiesToUpload = activitiesRepository.getUnuploadedLoginActivities()

            activitiesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                val successfulUpdates = mutableMapOf<String, com.google.gson.JsonObject?>()

                batch.forEach { activityData ->
                    try {
                        val `object` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/login_activities", activityData.serialized
                        ).body()

                        successfulUpdates[activityData.id] = `object`
                    } catch (e: java.io.IOException) {
                        e.printStackTrace()
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    val idsToUpdate = successfulUpdates.keys.toTypedArray()
                    activitiesRepository.markActivitiesUploaded(idsToUpdate, successfulUpdates)
                }
            }

            uploadTeamActivitiesRefactored()

            withContext(Dispatchers.Main) {
                listener.onSuccess("User activities sync completed successfully")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                listener.onSuccess("Failed to upload user activities: ${e.message}")
            }
        }
    }

    private suspend fun uploadTeamActivitiesRefactored() {
        uploadCoordinator.upload(uploadConfigs.TeamActivitiesRefactored)
    }

    suspend fun uploadTeamActivities(apiInterface: ApiInterface) {
        data class TeamLogData(
            val id: String?,
            val time: Long?,
            val user: String?,
            val type: String?,
            val serialized: JsonObject
        )

        val logsData = databaseService.withRealm { realm ->
            val results = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
            results.map { log ->
                TeamLogData(
                    id = log.id,
                    time = log.time,
                    user = log.user,
                    type = log.type,
                    serialized = RealmTeamLog.serializeTeamActivities(log, context)
                )
            }
        }

        data class UploadResult(
            val id: String?,
            val time: Long?,
            val user: String?,
            val type: String?,
            val _id: String,
            val _rev: String
        )
        val successfulUploads = mutableListOf<UploadResult>()

        logsData.forEach { logData ->
            try {
                val `object` = apiInterface.postDoc(
                    UrlUtils.header, "application/json",
                    "${UrlUtils.getUrl()}/team_activities", logData.serialized
                ).body()

                if (`object` != null) {
                    val id = getString("id", `object`)
                    val rev = getString("rev", `object`)
                    successfulUploads.add(UploadResult(logData.id, logData.time, logData.user, logData.type, id, rev))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (successfulUploads.isNotEmpty()) {
            databaseService.executeTransactionAsync { realm ->
                val ids = successfulUploads.mapNotNull { it.id }
                val managedLogs = mutableMapOf<String, RealmTeamLog>()

                if (ids.isNotEmpty()) {
                    ids.chunked(999).forEach { chunk ->
                        val results = realm.where(RealmTeamLog::class.java)
                            .`in`("id", chunk.toTypedArray())
                            .findAll()
                        results.forEach { log ->
                            log.id?.let { id -> managedLogs[id] = log }
                        }
                    }
                }

                val uploadsWithoutId = successfulUploads.filter { it.id == null }
                val fallbackLogs = mutableMapOf<Triple<Long?, String?, String?>, RealmTeamLog>()

                if (uploadsWithoutId.isNotEmpty()) {
                    uploadsWithoutId.chunked(250).forEach { chunk ->
                        val query = realm.where(RealmTeamLog::class.java)
                        query.beginGroup()
                        chunk.forEachIndexed { index, upload ->
                            if (index > 0) query.or()
                            query.beginGroup()
                                .equalTo("time", upload.time)
                                .equalTo("user", upload.user)
                                .equalTo("type", upload.type)
                            .endGroup()
                        }
                        query.endGroup()

                        val results = query.findAll()
                        results.forEach { log ->
                            val key = Triple(log.time, log.user, log.type)
                            fallbackLogs[key] = log
                        }
                    }
                }

                successfulUploads.forEach { upload ->
                    val managedLog = if (upload.id != null) {
                        managedLogs[upload.id]
                    } else {
                        val key = Triple(upload.time, upload.user, upload.type)
                        fallbackLogs[key]
                    }
                    managedLog?._id = upload._id
                    managedLog?._rev = upload._rev
                }
            }
        }
    }

    suspend fun uploadRating() {
        uploadCoordinator.upload(uploadConfigs.Rating)
    }

    suspend fun uploadNews() {
        // Note: uploadNews has unique logic that requires uploading images BEFORE the news document,
        // then modifying the serialized JSON based on image upload responses. This doesn't fit the
        // standard UploadCoordinator pattern, so we handle it with custom logic but still use
        // the coordinator for the core upload/update flow where possible.

        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)
        val user = userRepository.getUserModelSuspending()

        data class NewsUploadData(
            val id: String?,
            val _id: String?,
            val message: String?,
            val imageUrls: List<String>,
            val newsJson: JsonObject
        )

        val newsItems = databaseService.withRealm { realm ->
            realm.where(RealmNews::class.java)
                .findAll()
                .mapNotNull { news ->
                    if (news.userId?.startsWith("guest") == true) null
                    else NewsUploadData(
                        id = news.id,
                        _id = news._id,
                        message = news.message,
                        imageUrls = news.imageUrls?.toList() ?: emptyList(),
                        newsJson = chatRepository.serializeNews(news)
                    )
                }
        }

        data class NewsUpdateData(
            val id: String?,
            val body: JsonObject?,
            val imagesArray: com.google.gson.JsonArray
        )

        withContext(Dispatchers.IO) {
            newsItems.chunked(BATCH_SIZE).forEach { batch ->
                val successfulUpdates = mutableListOf<NewsUpdateData>()
                batch.forEach { news ->
                    try {
                        // Upload images first and collect metadata
                        val imagesArray = com.google.gson.JsonArray()
                        var messageWithImages = news.message ?: ""

                        news.imageUrls.forEach { imageUrl ->
                            val imgObject = gson.fromJson(imageUrl, JsonObject::class.java)

                            // Create image resource document
                            val imageDoc = createImage(user, imgObject)
                            val imageResponse = apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/resources",
                                imageDoc
                            ).body()

                            val resourceId = getString("id", imageResponse)
                            val resourceRev = getString("rev", imageResponse)

                            // Upload image file as attachment
                            val imageFile = File(getString("imageUrl", imgObject))
                            val fileName = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                            val mimeType = imageFile.toURI().toURL().openConnection().contentType
                            val fileBody = FileUtils.fullyReadFileToBytes(imageFile)
                                .toRequestBody("application/octet-stream".toMediaTypeOrNull())

                            apiInterface.uploadResource(
                                getHeaderMap(mimeType, resourceRev),
                                "${UrlUtils.getUrl()}/resources/$resourceId/$fileName",
                                fileBody
                            )

                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            imagesArray.add(resourceObject)

                            messageWithImages += "\n$markdown"
                        }

                        val newsJson = news.newsJson
                        newsJson.addProperty("message", messageWithImages)
                        newsJson.add("images", imagesArray)

                        // Upload news document (POST or PUT)
                        val newsResponse = if (TextUtils.isEmpty(news._id)) {
                            apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/news",
                                newsJson
                            )
                        } else {
                            apiInterface.putDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/news/${news._id}",
                                newsJson
                            )
                        }

                        // Update database on success
                        if (newsResponse.isSuccessful && newsResponse.body() != null) {
                            successfulUpdates.add(NewsUpdateData(news.id, newsResponse.body(), imagesArray))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    databaseService.executeTransactionAsync { realm ->
                        val ids = successfulUpdates.mapNotNull { it.id }
                        val managedNewsMap = mutableMapOf<String, RealmNews>()

                        if (ids.isNotEmpty()) {
                            ids.chunked(999).forEach { chunk ->
                                val results = realm.where(RealmNews::class.java)
                                    .`in`("id", chunk.toTypedArray())
                                    .findAll()
                                results.forEach { n ->
                                    n.id?.let { id -> managedNewsMap[id] = n }
                                }
                            }
                        }

                        successfulUpdates.forEach { update ->
                            update.id?.let { id ->
                                managedNewsMap[id]?.let { managedNews ->
                                    managedNews.imageUrls?.clear()
                                    managedNews._id = getString("id", update.body)
                                    managedNews._rev = getString("rev", update.body)
                                    managedNews.images = gson.toJson(update.imagesArray)
                                }
                            }
                        }
                    }
                }
            }
        }
        uploadNewsActivities()
    }

    suspend fun uploadCrashLog() {
        uploadCoordinator.upload(uploadConfigs.CrashLog)
    }

    suspend fun uploadSearchActivity() {
        uploadCoordinator.upload(uploadConfigs.SearchActivity)
    }

    suspend fun uploadResourceActivities(type: String) {
        val config = if (type == "sync") {
            uploadConfigs.ResourceActivitiesSync
        } else {
            uploadConfigs.ResourceActivities
        }
        uploadCoordinator.upload(config)
    }

    suspend fun uploadCourseActivities() {
        uploadCoordinator.upload(uploadConfigs.CourseActivities)
    }

    suspend fun uploadMeetups() {
        uploadCoordinator.upload(uploadConfigs.Meetups)
    }

    suspend fun uploadAdoptedSurveys() {
        uploadCoordinator.upload(uploadConfigs.AdoptedSurveys)
    }
}
