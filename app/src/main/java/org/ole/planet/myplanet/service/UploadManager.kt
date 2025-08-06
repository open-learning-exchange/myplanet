package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.FileUploadService
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
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
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
    @AppPreferences private val pref: SharedPreferences
) : FileUploadService() {

    // Backward compatibility constructor for code that still uses singleton pattern
    constructor(context: Context) : this(
        context,
        DatabaseService(context),
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val gson = Gson()

    private fun getRealm(): Realm {
        return databaseService.realmInstance
    }

    private fun uploadNewsActivities() {
        Log.d("UploadManager", "Starting news activities upload")
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.executeTransactionAsync { realm: Realm ->
            val newsLog: List<RealmNewsLog> = realm.where(RealmNewsLog::class.java)
                .isNull("_id").or().isEmpty("_id")
                .findAll()

            newsLog.processInBatches { news ->
                    try {
                        Log.d("UploadManager", "Uploading news activity: ${news.id}")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", RealmNewsLog.serialize(news))?.execute()?.body()

                        if (`object` != null) {
                            news._id = getString("id", `object`)
                            news._rev = getString("rev", `object`)
                            Log.d("UploadManager", "News activity uploaded successfully: ${news._id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload news activity: ${news.id} - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading news activity: ${news.id}", e)
                        e.printStackTrace()
                    }
            }
        }

    }

    fun uploadActivities(listener: SuccessListener?) {
        Log.d("UploadManager", "Starting activities upload")
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            Log.w("UploadManager", "Cannot upload activities: user model is null")
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            Log.d("UploadManager", "Skipping activities upload for manager")
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        try {
            Log.d("UploadManager", "Posting normal MyPlanet activities")
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    Log.d("UploadManager", "Normal MyPlanet activities posted successfully")
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    Log.e("UploadManager", "Failed to post normal MyPlanet activities", t)
                }
            })

            apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    Log.d("UploadManager", "Retrieved existing MyPlanet activities")
                    var `object` = response.body()

                    if (`object` != null) {
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(MyPlanet.getTabletUsages(context))
                        `object`.add("usages", usages)
                    } else {
                        `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                    }

                    Log.d("UploadManager", "Uploading merged MyPlanet activities")
                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            Log.d("UploadManager", "MyPlanet activities uploaded successfully")
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                            Log.e("UploadManager", "Failed to upload activities", t)
                            listener?.onSuccess("Failed to upload activities: ${t.message}")
                        }
                    })
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    Log.w("UploadManager", "Failed to retrieve existing activities, uploading new ones", t)
                    val `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            Log.d("UploadManager", "MyPlanet activities uploaded successfully (fallback)")
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                            Log.e("UploadManager", "Failed to upload activities (fallback)", t)
                            listener?.onSuccess("Failed to upload activities: ${t.message}")
                        }
                    })
                }
            })
        } catch (e: Exception) {
            Log.e("UploadManager", "Exception during activities upload", e)
            e.printStackTrace()
            listener?.onSuccess("Failed to upload activities: ${e.message}")
        }
    }

    fun uploadExamResult(listener: SuccessListener) {
        Log.d("UploadManager", "Starting exam result upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync({ realm: Realm ->
            val submissions: List<RealmSubmission> = realm.where(RealmSubmission::class.java).findAll()

            submissions.processInBatches { sub ->
                    try {
                        if ((sub.answers?.size ?: 0) > 0) {
                            Log.d("UploadManager", "Uploading exam result: ${sub.id}")
                            RealmSubmission.continueResultUpload(sub, apiInterface, realm, context)
                            Log.d("UploadManager", "Exam result uploaded: ${sub.id}")
                        }
                    } catch (e: Exception) {
                        Log.e("UploadManager", "Error uploading exam result: ${sub.id}", e)
                        e.printStackTrace()
                    }
            }
        }, {
            Log.d("UploadManager", "Exam result upload completed, starting course progress upload")
            uploadCourseProgress()
            listener.onSuccess("Result sync completed successfully")
        }) { e: Throwable ->
            Log.e("UploadManager", "Error during exam result sync", e)
            e.printStackTrace()
            listener.onSuccess("Error during result sync: ${e.message}")
        }
    }

    private fun createImage(user: RealmUserModel?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", Date().time)
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("addedBy", user!!.id)
        `object`.addProperty("private", true)
        `object`.addProperty("resideOn", user.parentCode)
        `object`.addProperty("sourcePlanet", user.planetCode)
        val object1 = JsonObject()
        `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    fun uploadAchievement() {
        Log.d("UploadManager", "Starting achievement upload")
        val realm = getRealm()
        realm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmAchievement> = realm.where(RealmAchievement::class.java).findAll()
            list.processInBatches { sub ->
                try {
                    if (sub._id?.startsWith("guest") == true) {
                        Log.d("UploadManager", "Skipping guest achievement: ${sub._id}")
                        return@processInBatches
                    }
                    Log.d("UploadManager", "Processing achievement: ${sub._id}")
                } catch (e: IOException) {
                    Log.e("UploadManager", "Error processing achievement: ${sub._id}", e)
                    e.printStackTrace()
                }
            }
        }

    }

    private fun uploadCourseProgress() {
        Log.d("UploadManager", "Starting course progress upload")
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmCourseProgress> = realm.where(RealmCourseProgress::class.java).isNull("_id").findAll()
            var successCount = 0
            var skipCount = 0
            var errorCount = 0

            data.processInBatches { sub ->
                    try {
                        if (sub.userId?.startsWith("guest") == true) {
                            Log.d("UploadManager", "Skipping guest course progress: ${sub.userId}")
                            skipCount++
                            return@processInBatches
                        }

                        Log.d("UploadManager", "Uploading course progress: ${sub.courseId} for user ${sub.userId}")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/courses_progress", RealmCourseProgress.serializeProgress(sub))?.execute()?.body()
                        if (`object` != null) {
                            sub._id = getString("id", `object`)
                            sub._rev = getString("rev", `object`)
                            Log.d("UploadManager", "Course progress uploaded: ${sub._id}")
                            successCount++
                        } else {
                            Log.w("UploadManager", "Failed to upload course progress: ${sub.courseId} - No response")
                            errorCount++
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading course progress: ${sub.courseId}", e)
                        errorCount++
                        e.printStackTrace()
                    }
            }
            Log.d("UploadManager", "Course progress upload completed - Success: $successCount, Skip: $skipCount, Error: $errorCount")

        }
    }

    fun uploadFeedback(listener: SuccessListener) {
        Log.d("UploadManager", "Starting feedback upload")
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val feedbacks: List<RealmFeedback> = realm.where(RealmFeedback::class.java).findAll()

            if (feedbacks.isEmpty()) {
                Log.d("UploadManager", "No feedbacks to upload")
                return@Transaction
            }
            Log.d("UploadManager", "Found ${feedbacks.size} feedbacks to upload")

            var successCount = 0
            var errorCount = 0

            feedbacks.processInBatches { feedback ->
                try {
                    Log.d("UploadManager", "Uploading feedback: ${feedback.id}")
                    val res: Response<JsonObject>? = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/feedback", RealmFeedback.serializeFeedback(feedback))?.execute()

                    val r = res?.body()
                    if (r != null) {
                        val revElement = r["rev"]
                        val idElement = r["id"]
                        if (revElement != null && idElement != null) {
                            feedback._rev = revElement.asString
                            feedback._id = idElement.asString
                            Log.d("UploadManager", "Feedback uploaded: ${feedback._id}")
                            successCount++
                        } else {
                            Log.w("UploadManager", "Invalid response for feedback: ${feedback.id}")
                            errorCount++
                        }
                    } else {
                        Log.w("UploadManager", "No response for feedback: ${feedback.id}")
                        errorCount++
                    }
                } catch (e: IOException) {
                    Log.e("UploadManager", "Error uploading feedback: ${feedback.id}", e)
                    errorCount++
                    e.printStackTrace()
                }
            }
            Log.d("UploadManager", "Feedback upload completed - Success: $successCount, Error: $errorCount")
        }, {
            Log.d("UploadManager", "Feedback sync completed successfully")
            listener.onSuccess("Feedback sync completed successfully")
        }, { error ->
            Log.e("UploadManager", "Feedback sync failed", error)
            listener.onSuccess("Feedback sync failed: ${error.message}")
            error.printStackTrace()
        })
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        Log.d("UploadManager", "Starting submit photos upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmSubmitPhotos> = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
            data.processInBatches { sub ->
                    try {
                        Log.d("UploadManager", "Uploading submit photo: ${sub.id}")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", RealmSubmitPhotos.serializeRealmSubmitPhotos(sub))?.execute()?.body()
                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            sub.uploaded = true
                            sub._rev = rev
                            sub._id = id
                            Log.d("UploadManager", "Submit photo uploaded: $id, uploading attachment")
                            uploadAttachment(id, rev, sub, listener!!)
                        } else {
                            Log.w("UploadManager", "Failed to upload submit photo: ${sub.id} - No response")
                        }
                    } catch (e: Exception) {
                        Log.e("UploadManager", "Error uploading submit photo: ${sub.id}", e)
                        e.printStackTrace()
                    }
            }
            if (data.isEmpty()) {
                Log.d("UploadManager", "No photos to upload")
                listener?.onSuccess("No photos to upload")
            } else {
                Log.d("UploadManager", "Found ${data.size} photos to upload")
            }
        }
    }

    fun uploadResource(listener: SuccessListener?) {
        Log.d("UploadManager", "Starting resource upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync({ realm: Realm ->
            val user = realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()

            val data: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java)
                .isNull("_rev")
                .findAll()

            if (data.isEmpty()) {
                Log.d("UploadManager", "No resources to upload")
                return@executeTransactionAsync
            }
            Log.d("UploadManager", "Found ${data.size} resources to upload")

            data.processInBatches { sub ->
                try {
                    Log.d("UploadManager", "Uploading resource: ${sub.id}")
                    val `object` = apiInterface?.postDoc(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/resources",
                        RealmMyLibrary.serialize(sub, user)
                    )?.execute()?.body()

                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)
                        sub._rev = rev
                        sub._id = id
                        Log.d("UploadManager", "Resource uploaded: $id, uploading attachment")
                        uploadAttachment(id, rev, sub, listener!!)
                    } else {
                        Log.w("UploadManager", "Failed to upload resource: ${sub.id} - No response")
                    }
                } catch (e: Exception) {
                    Log.e("UploadManager", "Error uploading resource: ${sub.id}", e)
                    e.printStackTrace()
                }
            }
        }, {
            Log.d("UploadManager", "Resource upload transaction completed")
            listener?.onSuccess("No resources to upload")
        }) { error ->
            Log.e("UploadManager", "Resource upload failed", error)
            listener?.onSuccess("Resource upload failed: ${error.message}")
        }
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        Log.d("UploadManager", "Starting personal resource upload: ${personal.id}")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
            Log.d("UploadManager", "Uploading personal resource data")
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", RealmMyPersonal.serialize(personal, context))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    val `object` = response.body()
                    if (`object` != null) {
                        try {
                            if (!realm.isInTransaction) {
                                realm.beginTransaction()
                            }
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            personal.isUploaded = true
                            personal._rev = rev
                            personal._id = id
                            realm.commitTransaction()
                            Log.d("UploadManager", "Personal resource uploaded: $id, uploading attachment")
                            uploadAttachment(id, rev, personal, listener)
                        } catch (e: Exception) {
                            Log.e("UploadManager", "Error updating personal resource", e)
                            if (realm.isInTransaction) {
                                realm.cancelTransaction()
                            }
                            listener.onSuccess("Error updating personal resource: ${e.message}")
                        }
                    } else {
                        Log.w("UploadManager", "Failed to upload personal resource: No response")
                        listener.onSuccess("Failed to upload personal resource: No response")
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    Log.e("UploadManager", "Failed to upload personal resource", t)
                    listener.onSuccess("Unable to upload resource: ${t.message}")
                }
            })
        } else {
            Log.d("UploadManager", "Personal resource already uploaded: ${personal.id}")
            listener.onSuccess("Resource already uploaded")
        }
    }

    fun uploadTeamTask() {
        Log.d("UploadManager", "Starting team task upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmTeamTask> = realm.where(RealmTeamTask::class.java).findAll()
            val tasksToUpload = list.filter { task ->
                TextUtils.isEmpty(task._id) || task.isUpdated
            }

            Log.d("UploadManager", "Found ${tasksToUpload.size} team tasks to upload")
            tasksToUpload.processInBatches { task ->
                    try {
                        Log.d("UploadManager", "Uploading team task: ${task._id ?: "new"}")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/tasks", RealmTeamTask.serialize(realm, task))?.execute()?.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            task._rev = rev
                            task._id = id
                            Log.d("UploadManager", "Team task uploaded: $id")
                        } else {
                            Log.w("UploadManager", "Failed to upload team task - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading team task: ${task._id}", e)
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadSubmissions() {
        Log.d("UploadManager", "Starting submissions upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmSubmission> = realm.where(RealmSubmission::class.java)
                .equalTo("isUpdated", true).or().isEmpty("_id").findAll()

            Log.d("UploadManager", "Found ${list.size} submissions to upload")
            list.processInBatches { submission ->
                    try {
                        Log.d("UploadManager", "Uploading submission: ${submission._id ?: "new"}")
                        val requestJson = RealmSubmission.serialize(realm, submission)
                        val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", requestJson)?.execute()

                        val jsonObject = response?.body()
                        if (jsonObject != null) {
                            val rev = getString("rev", jsonObject)
                            val id = getString("id", jsonObject)
                            submission._rev = rev
                            submission._id = id
                            submission.isUpdated = false
                            Log.d("UploadManager", "Submission uploaded: $id")
                        } else {
                            Log.w("UploadManager", "Failed to upload submission - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading submission: ${submission._id}", e)
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadTeams() {
        Log.d("UploadManager", "Starting teams upload")
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()

        realm.executeTransactionAsync { realm: Realm ->
            val teams: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java).equalTo("updated", true).findAll()
            Log.d("UploadManager", "Found ${teams.size} teams to upload")
            teams.processInBatches { team ->
                    try {
                        Log.d("UploadManager", "Uploading team: ${team._id}")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/teams", RealmMyTeam.serialize(team))?.execute()?.body()
                        if (`object` != null) {
                            team._rev = getString("rev", `object`)
                            team.updated = false
                            Log.d("UploadManager", "Team uploaded: ${team._id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload team: ${team._id} - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading team: ${team._id}", e)
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadUserActivities(listener: SuccessListener) {
        Log.d("UploadManager", "Starting user activities upload")
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            Log.w("UploadManager", "Cannot upload user activities: user model is null")
            listener.onSuccess("Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            Log.d("UploadManager", "Skipping user activities upload for manager")
            listener.onSuccess("Skipping user activities upload for manager")
            return
        }

        val realm = getRealm()
        realm.executeTransactionAsync({ transactionRealm: Realm ->
            val activities = transactionRealm.where(RealmOfflineActivity::class.java).isNull("_rev").equalTo("type", "login").findAll()

            Log.d("UploadManager", "Found ${activities.size} user activities to upload")
            activities.processInBatches { act ->
                try {
                    if (act.userId?.startsWith("guest") == true) {
                        Log.d("UploadManager", "Skipping guest user activity: ${act.userId}")
                        return@processInBatches
                    }

                    Log.d("UploadManager", "Uploading user activity: ${act.id}")
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/login_activities", RealmOfflineActivity.serializeLoginActivities(act, context))?.execute()?.body()
                    act.changeRev(`object`)
                    if (`object` != null) {
                        Log.d("UploadManager", "User activity uploaded: ${act.id}")
                    } else {
                        Log.w("UploadManager", "Failed to upload user activity: ${act.id} - No response")
                    }
                } catch (e: IOException) {
                    Log.e("UploadManager", "Error uploading user activity: ${act.id}", e)
                    e.printStackTrace()
                }
            }
            uploadTeamActivities(transactionRealm, apiInterface)
        }, {
            Log.d("UploadManager", "User activities sync completed successfully")
            realm.close()
            listener.onSuccess("User activities sync completed successfully")
        }) { e: Throwable ->
            Log.e("UploadManager", "User activities sync failed", e)
            realm.close()
            e.printStackTrace()
            listener.onSuccess(e.message)
        }
    }

    fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        Log.d("UploadManager", "Starting team activities upload")
        val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
        Log.d("UploadManager", "Found ${logs.size} team activities to upload")
        logs.processInBatches { log ->
                try {
                    Log.d("UploadManager", "Uploading team activity: ${log.id}")
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))?.execute()?.body()
                    if (`object` != null) {
                        log._id = getString("id", `object`)
                        log._rev = getString("rev", `object`)
                        Log.d("UploadManager", "Team activity uploaded: ${log._id}")
                    } else {
                        Log.w("UploadManager", "Failed to upload team activity: ${log.id} - No response")
                    }
                } catch (e: IOException) {
                    Log.e("UploadManager", "Error uploading team activity: ${log.id}", e)
                    e.printStackTrace()
                }
        }
    }

    fun uploadRating() {
        Log.d("UploadManager", "Starting rating upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()
            Log.d("UploadManager", "Found ${activities.size} ratings to upload")
            activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            Log.d("UploadManager", "Skipping guest rating: ${act.userId}")
                            return@processInBatches
                        }

                        val isNewRating = TextUtils.isEmpty(act._id)
                        Log.d("UploadManager", "Uploading rating: ${act._id ?: "new"} for item ${act.item}")
                        
                        val `object`: Response<JsonObject>? =
                            if (isNewRating) {
                                apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/ratings", RealmRating.serializeRating(act))?.execute()
                            } else {
                                apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/ratings/" + act._id, RealmRating.serializeRating(act))?.execute()
                            }
                        if (`object`?.body() != null) {
                            act._id = getString("id", `object`.body())
                            act._rev = getString("rev", `object`.body())
                            act.isUpdated = false
                            Log.d("UploadManager", "Rating uploaded: ${act._id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload rating for item ${act.item} - No response")
                        }
                    } catch (e: Exception) {
                        Log.e("UploadManager", "Error uploading rating for item ${act.item}", e)
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadNews() {
        Log.d("UploadManager", "Starting news upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmNews::class.java).findAll()
            Log.d("UploadManager", "Found ${activities.size} news items to upload")
            activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            Log.d("UploadManager", "Skipping guest news: ${act.userId}")
                            return@processInBatches
                        }
                        Log.d("UploadManager", "Uploading news: ${act._id ?: "new"}")

                        val `object` = RealmNews.serializeNews(act)
                        val image = act.imagesArray
                        val user = realm.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()

                        if (act.imageUrls != null && act.imageUrls?.isNotEmpty() == true) {
                            act.imageUrls?.chunked(5)?.forEach { imageChunk ->
                                imageChunk.forEach { imageObject ->
                                    val imgObject = gson.fromJson(imageObject, JsonObject::class.java)
                                    val ob = createImage(user, imgObject)
                                    val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", ob)?.execute()?.body()

                                    val rev = getString("rev", response)
                                    val id = getString("id", response)
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = FileUtils.fullyReadFileToBytes(f)
                                        .toRequestBody("application/octet-stream".toMediaTypeOrNull())
                                    val url = String.format(format, Utilities.getUrl(), id, name)

                                    val res = apiInterface?.uploadResource(getHeaderMap(mimeType, rev), url, body)?.execute()
                                    val attachment = res?.body()

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

                        act.images = gson.toJson(image)
                        `object`.add("images", image)

                        val newsUploadResponse: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/news", `object`)?.execute()
                            } else {
                                apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/news/" + act._id, `object`)?.execute()
                            }
                        if (newsUploadResponse?.body() != null) {
                            act.imageUrls?.clear()
                            act._id = getString("id", newsUploadResponse.body())
                            act._rev = getString("rev", newsUploadResponse.body())
                            Log.d("UploadManager", "News uploaded: ${act._id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload news - No response")
                        }
                    } catch (e: Exception) {
                        Log.e("UploadManager", "Error uploading news: ${act._id}", e)
                        e.printStackTrace()
                    }
                }
            }
        uploadNewsActivities()
    }

    fun uploadCrashLog() {
        Log.d("UploadManager", "Starting crash log upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        try {
            val hasLooper = Looper.myLooper() != null

            if (hasLooper) {
                realm.executeTransactionAsync { realm: Realm ->
                    uploadCrashLogData(realm, apiInterface)
                }
            } else {
                realm.executeTransaction { realm: Realm ->
                    uploadCrashLogData(realm, apiInterface)
                }
            }
        } catch (e: Exception) {
            Log.e("UploadManager", "Error during crash log upload", e)
            e.printStackTrace()
        }
    }

    private fun uploadCrashLogData(realm: Realm, apiInterface: ApiInterface?) {
        val logs: RealmResults<RealmApkLog> = realm.where(RealmApkLog::class.java).isNull("_rev").findAll()
        Log.d("UploadManager", "Found ${logs.size} crash logs to upload")

        logs.processInBatches { act ->
                try {
                    Log.d("UploadManager", "Uploading crash log: ${act.id}")
                    val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/apk_logs", RealmApkLog.serialize(act, context))?.execute()?.body()

                    if (o != null) {
                        act._rev = getString("rev", o)
                        Log.d("UploadManager", "Crash log uploaded: ${act.id}")
                    } else {
                        Log.w("UploadManager", "Failed to upload crash log: ${act.id} - No response")
                    }
                } catch (e: IOException) {
                    Log.e("UploadManager", "Error uploading crash log: ${act.id}", e)
                    e.printStackTrace()
                }
        }
    }

    fun uploadSearchActivity() {
        Log.d("UploadManager", "Starting search activity upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val logs: RealmResults<RealmSearchActivity> = realm.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll()
            Log.d("UploadManager", "Found ${logs.size} search activities to upload")
            logs.processInBatches { act ->
                    try {
                        Log.d("UploadManager", "Uploading search activity: ${act.id}")
                        val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/search_activities", act.serialize())?.execute()?.body()
                        if (o != null) {
                            act._rev = getString("rev", o)
                            Log.d("UploadManager", "Search activity uploaded: ${act.id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload search activity: ${act.id} - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading search activity: ${act.id}", e)
                        e.printStackTrace()
                    }
            }
        }

    }

    fun uploadResourceActivities(type: String) {
        Log.d("UploadManager", "Starting resource activities upload for type: $type")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }

        realm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmResourceActivity> =
                if (type == "sync") {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
            Log.d("UploadManager", "Found ${activities.size} resource activities to upload for $type")
            activities.processInBatches { act ->
                    try {
                        Log.d("UploadManager", "Uploading resource activity: ${act.id} (type: $type)")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/" + db, RealmResourceActivity.serializeResourceActivities(act))?.execute()?.body()

                        if (`object` != null) {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                            Log.d("UploadManager", "Resource activity uploaded: ${act._id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload resource activity: ${act.id} - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading resource activity: ${act.id}", e)
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadCourseActivities() {
        Log.d("UploadManager", "Starting course activities upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmCourseActivity> = realm.where(RealmCourseActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
            Log.d("UploadManager", "Found ${activities.size} course activities to upload")
            activities.processInBatches { act ->
                    try {
                        Log.d("UploadManager", "Uploading course activity: ${act.id}")
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/course_activities", RealmCourseActivity.serializeSerialize(act))?.execute()?.body()

                        if (`object` != null) {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                            Log.d("UploadManager", "Course activity uploaded: ${act._id}")
                        } else {
                            Log.w("UploadManager", "Failed to upload course activity: ${act.id} - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading course activity: ${act.id}", e)
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadMeetups() {
        Log.d("UploadManager", "Starting meetups upload")
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val meetups: List<RealmMeetup> = realm.where(RealmMeetup::class.java).findAll()
            Log.d("UploadManager", "Found ${meetups.size} meetups to upload")
            meetups.processInBatches { meetup ->
                    try {
                        Log.d("UploadManager", "Uploading meetup: ${meetup.meetupId ?: "new"}")
                        val meetupJson = RealmMeetup.serialize(meetup)
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/meetups", meetupJson)?.execute()?.body()

                        if (`object` != null) {
                            meetup.meetupId = getString("id", `object`)
                            meetup.meetupIdRev = getString("rev", `object`)
                            Log.d("UploadManager", "Meetup uploaded: ${meetup.meetupId}")
                        } else {
                            Log.w("UploadManager", "Failed to upload meetup - No response")
                        }
                    } catch (e: IOException) {
                        Log.e("UploadManager", "Error uploading meetup: ${meetup.meetupId}", e)
                        e.printStackTrace()
                    }
            }
        }
    }
}
