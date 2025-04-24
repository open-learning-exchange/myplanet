package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmResults
import okhttp3.MediaType
import okhttp3.RequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.FileUploadService
import org.ole.planet.myplanet.model.MyPlanet.Companion.getMyPlanetActivities
import org.ole.planet.myplanet.model.MyPlanet.Companion.getNormalMyPlanetActivities
import org.ole.planet.myplanet.model.MyPlanet.Companion.getTabletUsages
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmApkLog.Companion.serialize
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseActivity.Companion.serializeSerialize
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.serializeProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmFeedback.Companion.serializeFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMeetup.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmMyPersonal.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.serialize
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.serializeNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmNewsLog.Companion.serialize
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmOfflineActivity.Companion.serializeLoginActivities
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmRating.Companion.serializeRating
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmResourceActivity.Companion.serializeResourceActivities
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.continueResultUpload
import org.ole.planet.myplanet.model.RealmSubmission.Companion.serialize
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmSubmitPhotos.Companion.serializeRealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamLog.Companion.serializeTeamActivities
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmTeamTask.Companion.serialize
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils.fullyReadFileToBytes
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.getDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.getUniqueIdentifier
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils.getAndroidId
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class UploadManager(var context: Context) : FileUploadService() {
    var pref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dbService: DatabaseService = DatabaseService(context)
    lateinit var mRealm: Realm

    companion object {
        private const val BATCH_SIZE = 50
        private const val TAG = "UploadManager"

        var instance: UploadManager? = null
            get() {
                if (field == null) {
                    field = UploadManager(MainApplication.context)
                }
                return field
            }
            private set
    }

    // Helper method to safely get a Realm instance
    private fun getRealm(): Realm {
        return if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm
        } else {
            dbService.realmInstance
        }
    }

    // Helper method to format timestamp
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun uploadNewsActivities() {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = getRealm()

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadNewsActivities at ${formatTime(startTime)}")

        mRealm.executeTransactionAsync { realm: Realm ->
            val newsLog: List<RealmNewsLog> = realm.where(RealmNewsLog::class.java)
                .isNull("_id").or().isEmpty("_id")
                .findAll()

            Log.d(TAG, "Processing ${newsLog.size} news log items")

            // Process in batches
            newsLog.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(
                    TAG,
                    "Processing news batch ${batchIndex + 1}/${ceil(newsLog.size.toDouble() / BATCH_SIZE).toInt()}"
                )

                batch.forEach { news ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/myplanet_activities",
                            serialize(news)
                        )?.execute()?.body()

                        if (`object` != null) {
                            news._id = getString("id", `object`)
                            news._rev = getString("rev", `object`)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading news: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadNewsActivities in ${endTime - startTime}ms")
    }

    fun uploadActivities(listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            Log.e(TAG, "Cannot upload activities: user model is null")
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            Log.d(TAG, "Skipping activities upload for manager")
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadActivities at ${formatTime(startTime)}")

        try {
            // First API call
            apiInterface?.postDoc(
                Utilities.header,
                "application/json",
                "${Utilities.getUrl()}/myplanet_activities",
                getNormalMyPlanetActivities(MainApplication.context, pref, model)
            )?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    Log.d(TAG, "First API call completed with status: ${response.code()}")
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    Log.e(TAG, "First API call failed: ${t.message}")
                }
            })

            // Second API call
            apiInterface?.getJsonObject(
                Utilities.header,
                "${Utilities.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${getUniqueIdentifier()}"
            )?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    var `object` = response.body()

                    if (`object` != null) {
                        Log.d(TAG, "Retrieved existing activities object")
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(getTabletUsages(context))
                        `object`.add("usages", usages)
                    } else {
                        Log.d(TAG, "Creating new activities object")
                        `object` = getMyPlanetActivities(context, pref, model)
                    }

                    // Final API call to update activities
                    apiInterface.postDoc(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/myplanet_activities",
                        `object`
                    ).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            val endTime = System.currentTimeMillis()
                            Log.d(TAG, "Completed uploadActivities in ${endTime - startTime}ms")
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                            Log.e(TAG, "Final activities update failed: ${t.message}")
                            listener?.onSuccess("Failed to upload activities: ${t.message}")
                        }
                    })
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    Log.e(TAG, "Failed to get existing activities: ${t.message}")

                    // Create a new object if we can't retrieve the existing one
                    val `object` = getMyPlanetActivities(context, pref, model)

                    apiInterface.postDoc(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/myplanet_activities",
                        `object`
                    ).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            val endTime = System.currentTimeMillis()
                            Log.d(TAG, "Completed uploadActivities (fallback path) in ${endTime - startTime}ms")
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                            Log.e(TAG, "Final activities update failed (fallback path): ${t.message}")
                            listener?.onSuccess("Failed to upload activities: ${t.message}")
                        }
                    })
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception in uploadActivities: ${e.message}")
            e.printStackTrace()
            listener?.onSuccess("Failed to upload activities: ${e.message}")
        }
    }

    fun uploadExamResult(listener: SuccessListener) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadExamResult at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync({ realm: Realm ->
            val submissions: List<RealmSubmission> = realm.where(RealmSubmission::class.java).findAll()

            Log.d(TAG, "Processing ${submissions.size} exam submissions")

            // Process submissions in batches
            submissions.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing submissions batch ${batchIndex+1}/${ceil(submissions.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { sub ->
                    try {
                        if ((sub.answers?.size ?: 0) > 0) {
                            continueResultUpload(sub, apiInterface, realm, context)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading submission: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }, {
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadExamResult in ${endTime - startTime}ms")

            // Upload course progress in a separate transaction
            uploadCourseProgress()

            listener.onSuccess("Result sync completed successfully")
        }) { e: Throwable ->
            Log.e(TAG, "Failed to upload exam results: ${e.message}")
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
        `object`.addProperty("androidId", getUniqueIdentifier())
        `object`.addProperty("deviceName", getDeviceName())
        `object`.addProperty("customDeviceName", getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    fun uploadAchievement() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadAchievement at ${formatTime(startTime)}")

        mRealm = getRealm()
        // Note: This method doesn't actually upload anything as apiInterface calls are commented out
        // Keeping batching logic for if it gets uncommented in the future

        mRealm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmAchievement> = realm.where(RealmAchievement::class.java).findAll()

            Log.d(TAG, "Processing ${list.size} achievements")

            // Process achievements in batches
            list.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing achievements batch ${batchIndex+1}/${ceil(list.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { sub ->
                    try {
                        if (sub._id?.startsWith("guest") == true) {
                            return@forEach
                        }
                        // Note: These API calls are commented out in original code
                        // If uncommented in the future, batching is already implemented
                    } catch (e: IOException) {
                        Log.e(TAG, "Error processing achievement: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadAchievement in ${endTime - startTime}ms")
    }

    private fun uploadCourseProgress() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadCourseProgress at ${formatTime(startTime)}")

        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = getRealm()

        mRealm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmCourseProgress> = realm.where(RealmCourseProgress::class.java)
                .isNull("_id")
                .findAll()

            Log.d(TAG, "Processing ${data.size} course progress records")

            var successCount = 0
            var skipCount = 0
            var errorCount = 0

            // Process in batches
            data.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing course progress batch ${batchIndex+1}/${ceil(data.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { sub ->
                    try {
                        if (sub.userId?.startsWith("guest") == true) {
                            skipCount++
                            return@forEach
                        }

                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/courses_progress",
                            serializeProgress(sub)
                        )?.execute()?.body()

                        if (`object` != null) {
                            sub._id = getString("id", `object`)
                            sub._rev = getString("rev", `object`)
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading course progress: ${e.message}")
                        errorCount++
                        e.printStackTrace()
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadCourseProgress in ${endTime - startTime}ms: $successCount successful, $skipCount skipped, $errorCount failed")
        }
    }

    fun uploadFeedback(listener: SuccessListener) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadFeedback at ${formatTime(startTime)}")

        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = getRealm()

        mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val feedbacks: List<RealmFeedback> = realm.where(RealmFeedback::class.java).findAll()

            Log.d(TAG, "Processing ${feedbacks.size} feedback items")

            var successCount = 0
            var errorCount = 0

            // Process in batches
            feedbacks.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing feedback batch ${batchIndex+1}/${ceil(feedbacks.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { feedback ->
                    try {
                        val res: Response<JsonObject>? = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/feedback",
                            serializeFeedback(feedback)
                        )?.execute()

                        val r = res?.body()
                        if (r != null) {
                            val revElement = r["rev"]
                            val idElement = r["id"]
                            if (revElement != null && idElement != null) {
                                feedback._rev = revElement.asString
                                feedback._id = idElement.asString
                                successCount++
                            } else {
                                errorCount++
                            }
                        } else {
                            errorCount++
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading feedback: ${e.message}")
                        errorCount++
                        e.printStackTrace()
                    }
                }
            }

            Log.d(TAG, "Feedback upload results: $successCount successful, $errorCount failed")
        }, Realm.Transaction.OnSuccess {
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadFeedback in ${endTime - startTime}ms")
            listener.onSuccess("Feedback sync completed successfully")
        })
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadSubmitPhotos at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmSubmitPhotos> = realm.where(RealmSubmitPhotos::class.java)
                .equalTo("uploaded", false)
                .findAll()

            Log.d(TAG, "Processing ${data.size} photo submissions")

            // Process in batches
            data.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing photo submissions batch ${batchIndex+1}/${ceil(data.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { sub ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/submissions",
                            serializeRealmSubmitPhotos(sub)
                        )?.execute()?.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            sub.uploaded = true
                            sub._rev = rev
                            sub._id = id

                            // Upload attachment separately to avoid memory issues with many attachments
                            uploadAttachment(id, rev, sub, listener!!)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading photo submission: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadSubmitPhotos in ${endTime - startTime}ms")

            // Ensure listener is called even if there were no photos to upload
            if (data.isEmpty()) {
                listener?.onSuccess("No photos to upload")
            }
        }
    }

    fun uploadResource(listener: SuccessListener?) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadResource at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val user = realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()

            val data: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java)
                .isNull("_rev")
                .findAll()

            Log.d(TAG, "Processing ${data.size} resources")

            // Process in batches
            data.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing resources batch ${batchIndex+1}/${ceil(data.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { sub ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/resources",
                            serialize(sub, user)
                        )?.execute()?.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            sub._rev = rev
                            sub._id = id

                            // Upload attachment separately
                            uploadAttachment(id, rev, sub, listener!!)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading resource: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadResource in ${endTime - startTime}ms")

            // Ensure listener is called even if there were no resources to upload
            if (data.isEmpty()) {
                listener?.onSuccess("No resources to upload")
            }
        }
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadMyPersonal at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
            apiInterface?.postDoc(
                Utilities.header,
                "application/json",
                Utilities.getUrl() + "/resources",
                serialize(personal, context)
            )?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    val `object` = response.body()
                    if (`object` != null) {
                        try {
                            if (!mRealm.isInTransaction) {
                                mRealm.beginTransaction()
                            }
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            personal.isUploaded = true
                            personal._rev = rev
                            personal._id = id
                            mRealm.commitTransaction()

                            // Upload attachment
                            uploadAttachment(id, rev, personal, listener)

                            val endTime = System.currentTimeMillis()
                            Log.d(TAG, "Completed uploadMyPersonal in ${endTime - startTime}ms")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in Realm transaction: ${e.message}")
                            if (mRealm.isInTransaction) {
                                mRealm.cancelTransaction()
                            }
                            listener.onSuccess("Error updating personal resource: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "Response body is null for personal resource upload")
                        listener.onSuccess("Failed to upload personal resource: No response")
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    Log.e(TAG, "Failed to upload personal resource: ${t.message}")
                    listener.onSuccess("Unable to upload resource: ${t.message}")
                }
            })
        } else {
            Log.d(TAG, "Personal resource already uploaded, skipping")
            listener.onSuccess("Resource already uploaded")
        }
    }

    fun uploadTeamTask() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadTeamTask at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmTeamTask> = realm.where(RealmTeamTask::class.java)
                .findAll()

            // Filter tasks that need uploading to reduce processing
            val tasksToUpload = list.filter { task ->
                TextUtils.isEmpty(task._id) || task.isUpdated
            }

            Log.d(TAG, "Processing ${tasksToUpload.size} team tasks out of ${list.size} total")

            // Process in batches
            tasksToUpload.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing team tasks batch ${batchIndex+1}/${ceil(tasksToUpload.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { task ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/tasks",
                            serialize(realm, task)
                        )?.execute()?.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            task._rev = rev
                            task._id = id
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading team task: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadTeamTask in ${endTime - startTime}ms")
    }

    fun uploadSubmissions() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadSubmissions at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmSubmission> = realm.where(RealmSubmission::class.java)
                .equalTo("isUpdated", true).or().isEmpty("_id")
                .findAll()

            Log.d(TAG, "Processing ${list.size} submissions")

            // Process in batches
            list.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing submissions batch ${batchIndex+1}/${ceil(list.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { submission ->
                    try {
                        val requestJson = serialize(realm, submission)
                        val response = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            "${Utilities.getUrl()}/submissions",
                            requestJson
                        )?.execute()

                        val jsonObject = response?.body()
                        if (jsonObject != null) {
                            val rev = getString("rev", jsonObject)
                            val id = getString("id", jsonObject)

                            submission._rev = rev
                            submission._id = id
                            submission.isUpdated = false
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading submission: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadSubmissions in ${endTime - startTime}ms")
    }

    fun uploadTeams() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadTeams at ${formatTime(startTime)}")

        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = getRealm()

        mRealm.executeTransactionAsync { realm: Realm ->
            val teams: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java)
                .equalTo("updated", true)
                .findAll()

            Log.d(TAG, "Processing ${teams.size} teams")

            // Process in batches
            teams.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing teams batch ${batchIndex+1}/${ceil(teams.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { team ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/teams",
                            serialize(team)
                        )?.execute()?.body()

                        if (`object` != null) {
                            team._rev = getString("rev", `object`)
                            team.updated = false
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading team: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadTeams in ${endTime - startTime}ms")
    }

    fun uploadUserActivities(listener: SuccessListener) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadUserActivities at ${formatTime(startTime)}")

        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = getRealm()

        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            Log.e(TAG, "Cannot upload user activities: user model is null")
            listener.onSuccess("Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            Log.d(TAG, "Skipping user activities upload for manager")
            listener.onSuccess("Skipping user activities upload for manager")
            return
        }

        mRealm.executeTransactionAsync({ realm: Realm ->
            val activities = realm.where(RealmOfflineActivity::class.java)
                .isNull("_rev")
                .equalTo("type", "login")
                .findAll()

            Log.d(TAG, "Processing ${activities.size} user activities")

            // Process in batches
            activities.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing user activities batch ${batchIndex+1}/${ceil(activities.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@forEach
                        }

                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/login_activities",
                            serializeLoginActivities(act, context)
                        )?.execute()?.body()

                        act.changeRev(`object`)
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading user activity: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // Upload team activities
            uploadTeamActivities(realm, apiInterface)
        }, {
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadUserActivities in ${endTime - startTime}ms")
            listener.onSuccess("User activities sync completed successfully")
        }) { e: Throwable ->
            Log.e(TAG, "Failed to upload user activities: ${e.message}")
            listener.onSuccess(e.message)
        }
    }

    private fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadTeamActivities at ${formatTime(startTime)}")

        val logs = realm.where(RealmTeamLog::class.java)
            .isNull("_rev")
            .findAll()

        Log.d(TAG, "Processing ${logs.size} team activity logs")

        // Process in batches
        logs.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            Log.d(TAG, "Processing team activities batch ${batchIndex+1}/${ceil(logs.size.toDouble() / BATCH_SIZE).toInt()}")

            batch.forEach { log ->
                try {
                    val `object` = apiInterface?.postDoc(
                        Utilities.header,
                        "application/json",
                        Utilities.getUrl() + "/team_activities",
                        serializeTeamActivities(log, context)
                    )?.execute()?.body()

                    if (`object` != null) {
                        log._id = getString("id", `object`)
                        log._rev = getString("rev", `object`)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error uploading team activity log: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadTeamActivities in ${endTime - startTime}ms")
    }

    fun uploadRating() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadRating at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmRating::class.java)
                .equalTo("isUpdated", true)
                .findAll()

            Log.d(TAG, "Processing ${activities.size} ratings")

            // Process in batches
            activities.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing ratings batch ${batchIndex+1}/${ceil(activities.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@forEach
                        }

                        val `object`: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(
                                    Utilities.header,
                                    "application/json",
                                    Utilities.getUrl() + "/ratings",
                                    serializeRating(act)
                                )?.execute()
                            } else {
                                apiInterface?.putDoc(
                                    Utilities.header,
                                    "application/json",
                                    Utilities.getUrl() + "/ratings/" + act._id,
                                    serializeRating(act)
                                )?.execute()
                            }

                        if (`object`?.body() != null) {
                            act._id = getString("id", `object`.body())
                            act._rev = getString("rev", `object`.body())
                            act.isUpdated = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading rating: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadRating in ${endTime - startTime}ms")
    }

    fun uploadNews() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadNews at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmNews::class.java).findAll()

            Log.d(TAG, "Processing ${activities.size} news items")

            // Process in batches
            activities.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing news batch ${batchIndex+1}/${ceil(activities.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@forEach
                        }

                        val `object` = serializeNews(act)
                        val image = act.imagesArray
                        val user = realm.where(RealmUserModel::class.java)
                            .equalTo("id", pref.getString("userId", ""))
                            .findFirst()

                        // Process images in smaller batches to avoid memory issues
                        if (act.imageUrls != null && act.imageUrls!!.isNotEmpty()) {
                            Log.d(TAG, "Processing ${act.imageUrls!!.size} images for news item")

                            // Process images in smaller batches (max 5 per batch)
                            act.imageUrls!!.chunked(5).forEach { imageChunk ->
                                imageChunk.forEach { imageObject ->
                                    val imgObject = Gson().fromJson(imageObject, JsonObject::class.java)
                                    val ob = createImage(user, imgObject)

                                    // Upload image resource
                                    val response = apiInterface?.postDoc(
                                        Utilities.header,
                                        "application/json",
                                        Utilities.getUrl() + "/resources",
                                        ob
                                    )?.execute()?.body()

                                    val rev = getString("rev", response)
                                    val id = getString("id", response)

                                    // Upload attachment
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = RequestBody.create(MediaType.parse("application/octet"), fullyReadFileToBytes(f))
                                    val url = String.format(format, Utilities.getUrl(), id, name)

                                    val res = apiInterface?.uploadResource(getHeaderMap(mimeType, rev), url, body)?.execute()
                                    val attachment = res?.body()

                                    // Update resource object
                                    val resourceObject = JsonObject()
                                    resourceObject.addProperty("resourceId", getString("id", attachment))
                                    resourceObject.addProperty("filename", getString("fileName", imgObject))
                                    val markdown = "![](resources/" + getString("id", attachment) + "/" + getString("fileName", imgObject) + ")"
                                    resourceObject.addProperty("markdown", markdown)

                                    // Append to message
                                    var msg = getString("message", `object`)
                                    msg += """
                                    
                                    $markdown
                                    """.trimIndent()
                                    `object`.addProperty("message", msg)
                                    image.add(resourceObject)
                                }
                            }
                        }

                        // Update news object with images
                        act.images = Gson().toJson(image)
                        `object`.add("images", image)

                        // Upload news
                        val newsUploadResponse: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(
                                    Utilities.header,
                                    "application/json",
                                    Utilities.getUrl() + "/news",
                                    `object`
                                )?.execute()
                            } else {
                                apiInterface?.putDoc(
                                    Utilities.header,
                                    "application/json",
                                    Utilities.getUrl() + "/news/" + act._id,
                                    `object`
                                )?.execute()
                            }

                        if (newsUploadResponse?.body() != null) {
                            act.imageUrls?.clear()
                            act._id = getString("id", newsUploadResponse.body())
                            act._rev = getString("rev", newsUploadResponse.body())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading news: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        // Also upload news activities
        uploadNewsActivities()

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadNews in ${endTime - startTime}ms")
    }

    fun uploadCrashLog() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadCrashLog at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val logs: RealmResults<RealmApkLog> = realm.where(RealmApkLog::class.java)
                .isNull("_rev")
                .findAll()

            Log.d(TAG, "Processing ${logs.size} crash logs")

            // Process in batches
            logs.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing crash logs batch ${batchIndex+1}/${ceil(logs.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        val o = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/apk_logs",
                            serialize(act, context)
                        )?.execute()?.body()

                        if (o != null) {
                            act._rev = getString("rev", o)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading crash log: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }, Realm.Transaction.OnSuccess {
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Completed uploadCrashLog in ${endTime - startTime}ms")
        })
    }

    fun uploadSearchActivity() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadSearchActivity at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val logs: RealmResults<RealmSearchActivity> = realm.where(RealmSearchActivity::class.java)
                .isEmpty("_rev")
                .findAll()

            Log.d(TAG, "Processing ${logs.size} search activities")

            // Process in batches
            logs.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing search activities batch ${batchIndex+1}/${ceil(logs.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        val o = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/search_activities",
                            act.serialize()
                        )?.execute()?.body()

                        if (o != null) {
                            act._rev = getString("rev", o)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading search activity: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadSearchActivity in ${endTime - startTime}ms")
    }

    fun uploadResourceActivities(type: String) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadResourceActivities with type '$type' at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }

        mRealm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmResourceActivity> =
                if (type == "sync") {
                    realm.where(RealmResourceActivity::class.java)
                        .isNull("_rev")
                        .equalTo("type", "sync")
                        .findAll()
                } else {
                    realm.where(RealmResourceActivity::class.java)
                        .isNull("_rev")
                        .notEqualTo("type", "sync")
                        .findAll()
                }

            Log.d(TAG, "Processing ${activities.size} resource activities")

            // Process in batches
            activities.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing resource activities batch ${batchIndex+1}/${ceil(activities.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/" + db,
                            serializeResourceActivities(act)
                        )?.execute()?.body()

                        if (`object` != null) {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading resource activity: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadResourceActivities in ${endTime - startTime}ms")
    }

    fun uploadCourseActivities() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadCourseActivities at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmCourseActivity> =
                realm.where(RealmCourseActivity::class.java)
                    .isNull("_rev")
                    .notEqualTo("type", "sync")
                    .findAll()

            Log.d(TAG, "Processing ${activities.size} course activities")

            // Process in batches
            activities.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing course activities batch ${batchIndex+1}/${ceil(activities.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { act ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/course_activities",
                            serializeSerialize(act)
                        )?.execute()?.body()

                        if (`object` != null) {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading course activity: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadCourseActivities in ${endTime - startTime}ms")
    }

    fun uploadMeetups() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting uploadMeetups at ${formatTime(startTime)}")

        mRealm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val meetups: List<RealmMeetup> = realm.where(RealmMeetup::class.java).findAll()

            Log.d(TAG, "Processing ${meetups.size} meetups")

            // Process in batches
            meetups.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing meetups batch ${batchIndex+1}/${ceil(meetups.size.toDouble() / BATCH_SIZE).toInt()}")

                batch.forEach { meetup ->
                    try {
                        val meetupJson = serialize(meetup)
                        val `object` = apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            Utilities.getUrl() + "/meetups",
                            meetupJson
                        )?.execute()?.body()

                        if (`object` != null) {
                            meetup.meetupId = getString("id", `object`)
                            meetup.meetupIdRev = getString("rev", `object`)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error uploading meetup: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Completed uploadMeetups in ${endTime - startTime}ms")
    }

    // Helper function for processing collections in batches
    private fun <T> processBatch(items: List<T>, batchSize: Int = BATCH_SIZE, processName: String, processItem: (T) -> Unit) {
        if (items.isEmpty()) {
            Log.d(TAG, "No $processName items to process")
            return
        }

        Log.d(TAG, "Processing ${items.size} $processName items")

        val totalBatches = ceil(items.size.toDouble() / batchSize).toInt()
        items.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            Log.d(TAG, "Processing $processName batch ${batchIndex+1}/$totalBatches")

            batch.forEach { item ->
                try {
                    processItem(item)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $processName item: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}
