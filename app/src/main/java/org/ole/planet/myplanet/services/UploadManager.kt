package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiClient.client
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.AppPreferences
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
    @param:AppPreferences private val pref: SharedPreferences,
    private val gson: Gson,
    private val uploadCoordinator: UploadCoordinator,
    private val personalsRepository: PersonalsRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : FileUploader() {

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.upload(UploadConfigs.NewsActivities)
    }

    fun uploadActivities(listener: OnSuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)
        val model = userRepository.getCurrentUser() ?: run {
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        MainApplication.applicationScope.launch {
            try {
                try {
                    apiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/myplanet_activities",
                        MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model)
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
                    `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
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
                val result = uploadCoordinator.upload(UploadConfigs.ExamResults)

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
        uploadCoordinator.upload(UploadConfigs.CourseProgress)
    }

    suspend fun uploadFeedback(): Boolean {
        return when (val result = uploadCoordinator.upload(UploadConfigs.Feedback)) {
            is UploadResult.Success -> true
            is UploadResult.PartialSuccess -> result.failed.isEmpty()
            is UploadResult.Failure -> false
            is UploadResult.Empty -> true
        }
    }

    suspend fun uploadSubmitPhotos(listener: OnSuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)

        data class PhotoData(
            val photoId: String?,
            val serialized: JsonObject
        )

        val photosToUpload = databaseService.withRealm { realm ->
            val data = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()

            if (data.isEmpty()) {
                emptyList()
            } else {
                data.map { photo ->
                    PhotoData(
                        photoId = photo.id,
                        serialized = RealmSubmitPhotos.serializeRealmSubmitPhotos(photo)
                    )
                }
            }
        }

        if (photosToUpload.isEmpty()) {
            listener?.onSuccess("No photos to upload")
            return
        }

        withContext(Dispatchers.IO) {
            photosToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { photoData ->
                    try {
                        val `object` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/submissions", photoData.serialized
                        ).body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)

                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmSubmitPhotos::class.java)
                                    .equalTo("id", photoData.photoId)
                                    .findFirst()?.let { sub ->
                                        sub.uploaded = true
                                        sub._rev = rev
                                        sub._id = id
                                    }
                            }

                            listener?.let {
                                val photo = databaseService.withRealm { realm ->
                                    realm.where(RealmSubmitPhotos::class.java)
                                        .equalTo("id", photoData.photoId).findFirst()
                                        ?.let { realm.copyFromRealm(it) }
                                }
                                photo?.let { uploadAttachment(id, rev, it, listener) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun uploadResource(listener: OnSuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)

        try {
            data class ResourceData(
                val libraryId: String?,
                val title: String?,
                val isPrivate: Boolean,
                val privateFor: String?,
                val serialized: JsonObject
            )

            val user = userRepository.getCurrentUser()

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
                    batch.forEach { resourceData ->
                        try {
                            val `object` = apiInterface.postDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/resources", resourceData.serialized
                            ).body()

                            if (`object` != null) {
                                val rev = getString("rev", `object`)
                                val id = getString("id", `object`)

                                databaseService.executeTransactionAsync { transactionRealm ->
                                    transactionRealm.where(RealmMyLibrary::class.java)
                                        .equalTo("id", resourceData.libraryId)
                                        .findFirst()?.let { sub ->
                                            sub._rev = rev
                                            sub._id = id
                                        }

                                    if (resourceData.isPrivate && !resourceData.privateFor.isNullOrBlank()) {
                                        val planetCode = user?.planetCode?.takeIf { it.isNotBlank() }
                                            ?: pref.getString("planetCode", "") ?: ""
                                        val teamResource = transactionRealm.createObject(
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

                                listener?.let {
                                    val library = databaseService.withRealm { realm ->
                                        realm.where(RealmMyLibrary::class.java)
                                            .equalTo("id", resourceData.libraryId).findFirst()
                                            ?.let { realm.copyFromRealm(it) }
                                    }
                                    library?.let { uploadAttachment(id, rev, it, listener) }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
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
        uploadCoordinator.upload(UploadConfigs.TeamTask)
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
            val result = uploadCoordinator.upload(UploadConfigs.Submissions)

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
        val apiInterface = client.create(ApiInterface::class.java)

        data class TeamData(
            val teamId: String?,
            val serialized: JsonObject
        )

        val teamsToUpload = databaseService.withRealm { realm ->
            val teams = realm.where(RealmMyTeam::class.java)
                .equalTo("updated", true).findAll()

            teams.map { team ->
                TeamData(
                    teamId = team._id,
                    serialized = RealmMyTeam.serialize(team)
                )
            }
        }

        withContext(Dispatchers.IO) {
            teamsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { teamData ->
                    try {
                        val `object` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/teams", teamData.serialized
                        ).body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)

                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmMyTeam::class.java)
                                    .equalTo("_id", teamData.teamId)
                                    .findFirst()?.let { team ->
                                        team._rev = rev
                                        team.updated = false
                                    }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun uploadUserActivities(listener: OnSuccessListener) {
        val apiInterface = client.create(ApiInterface::class.java)
        val model = userRepository.getCurrentUser() ?: run {
            listener.onSuccess("Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener.onSuccess("Skipping user activities upload for manager")
            return
        }

        try {
            data class ActivityData(
                val activityId: String?,
                val userId: String?,
                val serialized: JsonObject
            )

            val activitiesToUpload = databaseService.withRealm { realm ->
                val activities = realm.where(RealmOfflineActivity::class.java)
                    .isNull("_rev").equalTo("type", "login").findAll()

                activities.mapNotNull { activity ->
                    if (activity.userId?.startsWith("guest") == true) {
                        null
                    } else {
                        ActivityData(
                            activityId = activity.id,
                            userId = activity.userId,
                            serialized = RealmOfflineActivity.serializeLoginActivities(activity, context)
                        )
                    }
                }
            }

            activitiesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { activityData ->
                    try {
                        val `object` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/login_activities", activityData.serialized
                        ).body()

                        databaseService.executeTransactionAsync { transactionRealm ->
                            transactionRealm.where(RealmOfflineActivity::class.java)
                                .equalTo("id", activityData.activityId)
                                .findFirst()?.changeRev(`object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            uploadTeamActivitiesRefactored()

            listener.onSuccess("User activities sync completed successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSuccess("Failed to upload user activities: ${e.message}")
        }
    }

    private suspend fun uploadTeamActivitiesRefactored() {
        uploadCoordinator.upload(UploadConfigs.TeamActivitiesRefactored)
    }

    suspend fun uploadTeamActivities(apiInterface: ApiInterface) {
        data class TeamLogData(
            val time: Long?,
            val user: String?,
            val type: String?,
            val serialized: JsonObject
        )

        val logsData = databaseService.withRealm { realm ->
            val results = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
            results.map { log ->
                TeamLogData(
                    time = log.time,
                    user = log.user,
                    type = log.type,
                    serialized = RealmTeamLog.serializeTeamActivities(log, context)
                )
            }
        }

        logsData.forEach { logData ->
            try {
                val `object` = apiInterface.postDoc(
                    UrlUtils.header,
                    "application/json",
                    "${UrlUtils.getUrl()}/team_activities",
                    logData.serialized
                ).body()

                if (`object` != null) {
                    val id = getString("id", `object`)
                    val rev = getString("rev", `object`)
                    databaseService.executeTransactionAsync { realm ->
                        val managedLog = realm.where(RealmTeamLog::class.java)
                            .equalTo("time", logData.time)
                            .equalTo("user", logData.user)
                            .equalTo("type", logData.type)
                            .findFirst()
                        managedLog?._id = id
                        managedLog?._rev = rev
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun uploadRating() {
        uploadCoordinator.upload(UploadConfigs.Rating)
    }

    suspend fun uploadNews() {
        // Note: uploadNews has unique logic that requires uploading images BEFORE the news document,
        // then modifying the serialized JSON based on image upload responses. This doesn't fit the
        // standard UploadCoordinator pattern, so we handle it with custom logic but still use
        // the coordinator for the core upload/update flow where possible.

        val apiInterface = client.create(ApiInterface::class.java)
        val user = userRepository.getCurrentUser()

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

        withContext(Dispatchers.IO) {
            newsItems.chunked(BATCH_SIZE).forEach { batch ->
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

                            // Build image metadata and markdown
                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            imagesArray.add(resourceObject)

                            // Append markdown to message
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
                            databaseService.executeTransactionAsync { realm ->
                                realm.where(RealmNews::class.java)
                                    .equalTo("id", news.id)
                                    .findFirst()?.let { managedNews ->
                                        managedNews.imageUrls?.clear()
                                        managedNews._id = getString("id", newsResponse.body())
                                        managedNews._rev = getString("rev", newsResponse.body())
                                        managedNews.images = gson.toJson(imagesArray)
                                    }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        uploadNewsActivities()
    }

    suspend fun uploadCrashLog() {
        uploadCoordinator.upload(UploadConfigs.CrashLog)
    }

    suspend fun uploadSearchActivity() {
        uploadCoordinator.upload(UploadConfigs.SearchActivity)
    }

    suspend fun uploadResourceActivities(type: String) {
        val config = if (type == "sync") {
            UploadConfigs.ResourceActivitiesSync
        } else {
            UploadConfigs.ResourceActivities
        }
        uploadCoordinator.upload(config)
    }

    suspend fun uploadCourseActivities() {
        uploadCoordinator.upload(UploadConfigs.CourseActivities)
    }

    suspend fun uploadMeetups() {
        uploadCoordinator.upload(UploadConfigs.Meetups)
    }

    suspend fun uploadAdoptedSurveys() {
        uploadCoordinator.upload(UploadConfigs.AdoptedSurveys)
    }
}
