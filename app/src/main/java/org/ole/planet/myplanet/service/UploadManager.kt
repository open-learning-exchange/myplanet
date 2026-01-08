package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import io.realm.RealmResults
import java.io.File
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.data.ApiClient.client
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.VersionUtils.getAndroidId
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    private val submissionsRepository: SubmissionsRepository,
    @AppPreferences private val pref: SharedPreferences,
    private val gson: Gson,
    private val uploadCoordinator: org.ole.planet.myplanet.service.upload.UploadCoordinator
) : FileUploadService() {

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.NewsActivities)
    }

    fun uploadActivities(listener: SuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)
        val model = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        } ?: run {
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        try {
            apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model)).enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })

            apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")
                .enqueue(object : Callback<JsonObject?> {
                    override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                        var `object` = response.body()

                        if (`object` != null) {
                            val usages = `object`.getAsJsonArray("usages")
                            usages.addAll(MyPlanet.getTabletUsages(context))
                            `object`.add("usages", usages)
                        } else {
                            `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                        }

                        apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                                listener?.onSuccess("My planet activities uploaded successfully")
                            }

                            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                                listener?.onSuccess("Failed to upload activities: ${t.message}")
                            }
                        })
                    }

                    override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                        val `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                        apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                                listener?.onSuccess("My planet activities uploaded successfully")
                            }

                            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                                listener?.onSuccess("Failed to upload activities: ${t.message}")
                            }
                        })
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onSuccess("Failed to upload activities: ${e.message}")
        }
    }

    suspend fun uploadExamResult(listener: SuccessListener) {
        withContext(Dispatchers.IO) {
            val apiInterface = client.create(ApiInterface::class.java)
            try {
                val submissions = submissionsRepository.getAllPendingSubmissions()
                val submissionIds = submissions
                    .filter { (it.answers?.size ?: 0) > 0 && it.userId?.startsWith("guest") != true }
                    .mapNotNull { it.id }

                var processedCount = 0
                var errorCount = 0

                submissionIds.chunked(BATCH_SIZE).forEach { batchIds ->
                    val submissionsToUpload = databaseService.withRealm { realm ->
                        realm.where(RealmSubmission::class.java)
                            .`in`("id", batchIds.toTypedArray())
                            .findAll()
                            .map { sub ->
                                val serialized = RealmSubmission.serializeExamResult(realm, sub, context)
                                Triple(sub.id, serialized, sub._id)
                            }
                    }

                    for ((id, serialized, _id) in submissionsToUpload) {
                        try {
                            val response = if (TextUtils.isEmpty(_id)) {
                                apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions", serialized)
                            } else {
                                apiInterface.putDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions/$_id", serialized)
                            }

                            if (response.isSuccessful && id != null) {
                                val responseBody = response.body()
                                if (responseBody != null) {
                                    databaseService.withRealm { realm ->
                                        realm.executeTransaction { transactionRealm ->
                                            transactionRealm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()?.let { sub ->
                                                sub._id = getString("id", responseBody)
                                                sub._rev = getString("rev", responseBody)
                                            }
                                        }
                                    }
                                    processedCount++
                                } else {
                                    errorCount++
                                }
                            } else {
                                errorCount++
                            }
                        } catch (e: IOException) {
                            errorCount++
                            e.printStackTrace()
                        } catch (e: Exception) {
                            errorCount++
                            e.printStackTrace()
                        }
                    }
                }

                uploadCourseProgress()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Result sync completed successfully ($processedCount processed, $errorCount errors)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during result sync: ${e.message}")
                }
            }
        }
    }

    private fun createImage(user: RealmUserModel?, imgObject: JsonObject?): JsonObject {
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
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.CourseProgress)
    }

    suspend fun uploadFeedback(): Boolean {
        val result = uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.Feedback)
        return when (result) {
            is org.ole.planet.myplanet.service.upload.UploadResult.Success -> true
            is org.ole.planet.myplanet.service.upload.UploadResult.PartialSuccess -> result.failed.isEmpty()
            is org.ole.planet.myplanet.service.upload.UploadResult.Failure -> false
            is org.ole.planet.myplanet.service.upload.UploadResult.Empty -> true
        }
    }

    suspend fun uploadSubmitPhotos(listener: SuccessListener?) {
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
                    val copiedPhoto = realm.copyFromRealm(photo)
                    PhotoData(
                        photoId = copiedPhoto.id, serialized = RealmSubmitPhotos.serializeRealmSubmitPhotos(copiedPhoto)
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
                        val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/submissions", photoData.serialized
                        ).execute().body()

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

    suspend fun uploadResource(listener: SuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)

        try {
            data class ResourceData(
                val libraryId: String?,
                val serialized: JsonObject
            )

            val user = databaseService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("id", pref.getString("userId", "")).findFirst()
                    ?.let { realm.copyFromRealm(it) }
            }

            val resourcesToUpload = databaseService.withRealm { realm ->
                val data = realm.where(RealmMyLibrary::class.java).isNull("_rev").findAll()

                if (data.isEmpty()) {
                    emptyList()
                } else {
                    data.map { library ->
                        val copiedLibrary = realm.copyFromRealm(library)
                        ResourceData(
                            libraryId = copiedLibrary.id,
                            serialized = RealmMyLibrary.serialize(copiedLibrary, user)
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
                            val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/resources", resourceData.serialized
                            ).execute().body()

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
                    val response = apiInterface.postDoc(UrlUtils.header, "application/json",
                        "${UrlUtils.getUrl()}/resources", RealmMyPersonal.serialize(personal, context)
                    ).execute()

                    val `object` = response.body()
                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)

                        databaseService.executeTransactionAsync { transactionRealm ->
                            val managedPersonal = personal.id?.takeIf { it.isNotEmpty() }?.let { personalId ->
                                transactionRealm.where(RealmMyPersonal::class.java)
                                    .equalTo("id", personalId).findFirst()
                            } ?: personal._id?.takeIf { it.isNotEmpty() }?.let { existingId ->
                                transactionRealm.where(RealmMyPersonal::class.java)
                                    .equalTo("_id", existingId).findFirst()
                            }

                            managedPersonal?.let { realmPersonal ->
                                realmPersonal.isUploaded = true
                                realmPersonal._rev = rev
                                realmPersonal._id = id
                            } ?: throw IllegalStateException("Personal resource not found")
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
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.TeamTask)
    }

    suspend fun uploadSubmissions(buttonClickTime: Long = 0L) {
        Log.d("UploadManager", "uploadSubmissions called with buttonClickTime: $buttonClickTime")
        val startTime = if (buttonClickTime > 0) buttonClickTime else System.currentTimeMillis()

        if (buttonClickTime > 0) {
            Log.d("UploadManager", "Mini survey sync timer started from button click at: $startTime")
        } else {
            Log.d("UploadManager", "Mini survey sync started at: $startTime (buttonClickTime was $buttonClickTime)")
        }

        val apiInterface = client.create(ApiInterface::class.java)

        try {
            data class SubmissionData(
                val submissionId: String?,
                val submissionDbId: String?,
                val serialized: JsonObject
            )

            val submissionsToUpload = databaseService.withRealm { realm ->
                val list = realm.where(RealmSubmission::class.java)
                    .equalTo("isUpdated", true).or().isEmpty("_id").findAll()

                Log.d("UploadManager", "Found ${list.size} submissions to upload")
                if (list.isEmpty()) {
                    // Debug: Show all submissions to understand why none matched
                    val allSubmissions = realm.where(RealmSubmission::class.java).findAll()
                    Log.d("UploadManager", "Total submissions in DB: ${allSubmissions.size}")
                    allSubmissions.take(5).forEach { sub ->
                        Log.d("UploadManager", "  Submission: id=${sub.id}, _id=${sub._id}, isUpdated=${sub.isUpdated}, status=${sub.status}")
                    }
                } else {
                    list.forEach { sub ->
                        Log.d("UploadManager", "  Will upload: id=${sub.id}, _id=${sub._id}, isUpdated=${sub.isUpdated}")
                    }
                }

                list.map { submission ->
                    val copiedSubmission = realm.copyFromRealm(submission)
                    SubmissionData(
                        submissionId = copiedSubmission.id,
                        submissionDbId = copiedSubmission._id,
                        serialized = RealmSubmission.serialize(realm, copiedSubmission)
                    )
                }
            }

            submissionsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { submissionData ->
                    try {
                        val response = if (TextUtils.isEmpty(submissionData.submissionDbId)) {
                            apiInterface.postDoc(UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/submissions", submissionData.serialized
                            ).execute()
                        } else {
                            apiInterface.putDoc(UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/submissions/${submissionData.submissionDbId}",
                                submissionData.serialized
                            ).execute()
                        }

                        val jsonObject = response.body()
                        if (jsonObject != null) {
                            val rev = getString("rev", jsonObject)
                            val id = getString("id", jsonObject)

                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmSubmission::class.java)
                                    .equalTo("id", submissionData.submissionId)
                                    .findFirst()?.let { submission ->
                                        submission._rev = rev
                                        submission._id = id
                                        submission.isUpdated = false
                                    }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                val copiedTeam = realm.copyFromRealm(team)
                TeamData(
                    teamId = copiedTeam._id,
                    serialized = RealmMyTeam.serialize(copiedTeam)
                )
            }
        }

        withContext(Dispatchers.IO) {
            teamsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { teamData ->
                    try {
                        val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/teams", teamData.serialized).execute().body()

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

    suspend fun uploadUserActivities(listener: SuccessListener) {
        val apiInterface = client.create(ApiInterface::class.java)
        val model = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        } ?: run {
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
                        val copiedActivity = realm.copyFromRealm(activity)
                        ActivityData(
                            activityId = copiedActivity._id,
                            userId = copiedActivity.userId,
                            serialized = RealmOfflineActivity.serializeLoginActivities(copiedActivity, context)
                        )
                    }
                }
            }

            activitiesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { activityData ->
                    try {
                        val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/login_activities", activityData.serialized
                        ).execute().body()

                        databaseService.executeTransactionAsync { transactionRealm ->
                            transactionRealm.where(RealmOfflineActivity::class.java)
                                .equalTo("_id", activityData.activityId)
                                .findFirst()?.changeRev(`object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            uploadTeamActivitiesRefactored(apiInterface)

            listener.onSuccess("User activities sync completed successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSuccess("Failed to upload user activities: ${e.message}")
        }
    }

    private suspend fun uploadTeamActivitiesRefactored(apiInterface: ApiInterface?) {
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.TeamActivitiesRefactored)
    }

    fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
        logs.processInBatches { log ->
                try {
                    val `object` = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))?.execute()?.body()
                    if (`object` != null) {
                        log._id = getString("id", `object`)
                        log._rev = getString("rev", `object`)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }
    }

    suspend fun uploadRating() {
        val apiInterface = client.create(ApiInterface::class.java)

        data class RatingData(
            val ratingId: String?,
            val ratingDbId: String?,
            val userId: String?,
            val serialized: JsonObject
        )

        val ratingsToUpload = databaseService.withRealm { realm ->
            val activities = realm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()

            activities.mapNotNull { rating ->
                if (rating.userId?.startsWith("guest") == true) {
                    null
                } else {
                    val copiedRating = realm.copyFromRealm(rating)
                    RatingData(
                        ratingId = copiedRating.id,
                        ratingDbId = copiedRating._id,
                        userId = copiedRating.userId,
                        serialized = RealmRating.serializeRating(copiedRating)
                    )
                }
            }
        }

        withContext(Dispatchers.IO) {
            ratingsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { ratingData ->
                    try {
                        val `object`: Response<JsonObject>? =
                            if (TextUtils.isEmpty(ratingData.ratingDbId)) {
                                apiInterface.postDoc(UrlUtils.header, "application/json",
                                    "${UrlUtils.getUrl()}/ratings", ratingData.serialized
                                ).execute()
                            } else {
                                apiInterface.putDoc(UrlUtils.header, "application/json",
                                    "${UrlUtils.getUrl()}/ratings/${ratingData.ratingDbId}",
                                    ratingData.serialized
                                ).execute()
                            }

                        if (`object`?.body() != null) {
                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmRating::class.java)
                                    .equalTo("id", ratingData.ratingId)
                                    .findFirst()?.let { act ->
                                        act._id = getString("id", `object`.body())
                                        act._rev = getString("rev", `object`.body())
                                        act.isUpdated = false
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

    suspend fun uploadNews() {
        val apiInterface = client.create(ApiInterface::class.java)

        data class NewsData(
            val id: String,
            val newsId: String?,
            val userId: String?,
            val imageUrls: List<String>?,
            val serializedNews: JsonObject,
            val imagesArray: com.google.gson.JsonArray
        )

        val newsToUpload = databaseService.withRealm { realm ->
            val activities = realm.where(RealmNews::class.java).findAll()
            activities.mapNotNull { act ->
                if (act.userId?.startsWith("guest") == true) {
                    null
                } else {
                    val copiedAct = realm.copyFromRealm(act)
                    NewsData(
                        id = copiedAct.id ?: "",
                        newsId = copiedAct._id,
                        userId = copiedAct.userId,
                        imageUrls = copiedAct.imageUrls?.toList(),
                        serializedNews = RealmNews.serializeNews(copiedAct),
                        imagesArray = copiedAct.imagesArray
                    )
                }
            }
        }

        val user = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }

        withContext(Dispatchers.IO) {
            newsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { newsData ->
                    try {
                        val `object` = newsData.serializedNews
                        val image = newsData.imagesArray

                        if (!newsData.imageUrls.isNullOrEmpty()) {
                            newsData.imageUrls.chunked(5).forEach { imageChunk ->
                                imageChunk.forEach { imageObject ->
                                    val imgObject = gson.fromJson(imageObject, JsonObject::class.java)
                                    val ob = createImage(user, imgObject)
                                    val response = apiInterface.postDoc(UrlUtils.header,
                                        "application/json", "${UrlUtils.getUrl()}/resources", ob
                                    ).execute().body()

                                    val rev = getString("rev", response)
                                    val id = getString("id", response)
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = FileUtils.fullyReadFileToBytes(f).toRequestBody("application/octet-stream".toMediaTypeOrNull())
                                    val url = String.format(format, UrlUtils.getUrl(), id, name)

                                    val res = apiInterface.uploadResource(getHeaderMap(mimeType, rev), url, body).execute()
                                    val attachment = res.body()

                                    val resourceObject = JsonObject()
                                    resourceObject.addProperty("resourceId", getString("id", attachment))
                                    resourceObject.addProperty("filename", getString("fileName", imgObject))
                                    val markdown = "![](resources/" + getString("id", attachment) + "/" + getString("fileName", imgObject) + ")"
                                    resourceObject.addProperty("markdown", markdown)

                                    var msg = getString("message", `object`)
                                    msg += """
                                    $markdown
                                    """.trimIndent()
                                    `object`.addProperty("message", msg)
                                    image.add(resourceObject)
                                }
                            }
                        }

                        `object`.add("images", image)

                        val newsUploadResponse: Response<JsonObject>? =
                            if (TextUtils.isEmpty(newsData.newsId)) {
                                apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news", `object`).execute()
                            } else {
                                apiInterface.putDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news/${newsData.newsId}", `object`).execute()
                            }

                        if (newsUploadResponse?.body() != null) {
                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmNews::class.java)
                                    .equalTo("id", newsData.id)
                                    .findFirst()?.let { act ->
                                        act.imageUrls?.clear()
                                        act._id = getString("id", newsUploadResponse.body())
                                        act._rev = getString("rev", newsUploadResponse.body())
                                        act.images = gson.toJson(image)
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
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.CrashLog)
    }

    suspend fun uploadSearchActivity() {
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.SearchActivity)
    }

    suspend fun uploadResourceActivities(type: String) {
        val config = if (type == "sync") {
            org.ole.planet.myplanet.service.upload.UploadConfigs.ResourceActivitiesSync
        } else {
            org.ole.planet.myplanet.service.upload.UploadConfigs.ResourceActivities
        }
        uploadCoordinator.upload(config)
    }

    suspend fun uploadCourseActivities() {
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.CourseActivities)
    }

    suspend fun uploadMeetups() {
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.Meetups)
    }

    suspend fun uploadAdoptedSurveys() {
        uploadCoordinator.upload(org.ole.planet.myplanet.service.upload.UploadConfigs.AdoptedSurveys)
    }
}
