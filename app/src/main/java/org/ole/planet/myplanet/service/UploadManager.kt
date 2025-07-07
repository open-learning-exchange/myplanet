package org.ole.planet.myplanet.service

import android.content.*
import android.os.Looper
import android.text.TextUtils
import com.google.gson.*
import io.realm.*
import java.io.*
import java.util.Date
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils.getAndroidId
import retrofit2.*

private const val BATCH_SIZE = 50

private inline fun <T> Iterable<T>.processInBatches(action: (T) -> Unit) {
    chunked(BATCH_SIZE).forEach { chunk ->
        chunk.forEach { item ->
            action(item)
        }
    }
}

private inline fun <T> Realm.processBatch(
    crossinline query: (Realm) -> Iterable<T>,
    crossinline action: (Realm, T) -> Unit,
    noinline onSuccess: (() -> Unit)? = null,
    noinline onError: ((Throwable) -> Unit)? = null
) {
    executeTransactionAsync({ transactionRealm ->
        val items = query(transactionRealm)
        items.processInBatches { item ->
            try {
                action(transactionRealm, item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }, { onSuccess?.invoke() }) { error -> onError?.invoke(error) }
}

class UploadManager(var context: Context) : FileUploadService() {
    var pref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dbService: DatabaseService = DatabaseService(context)

    companion object {
        var instance: UploadManager? = null
            get() {
                if (field == null) {
                    field = UploadManager(MainApplication.context)
                }
                return field
            }
            private set
    }

    private fun getRealm(): Realm {
        return dbService.realmInstance
    }

    private fun uploadNewsActivities() {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.processBatch(
            query = {
                it.where(RealmNewsLog::class.java)
                    .isNull("_id").or().isEmpty("_id")
                    .findAll()
            },
            action = { _, news ->
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/myplanet_activities",
                    RealmNewsLog.serialize(news)
                )?.execute()?.body()

                if (obj != null) {
                    news._id = getString("id", obj)
                    news._rev = getString("rev", obj)
                }
            }
        )
    }

    fun uploadActivities(listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        try {
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })

            apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    var `object` = response.body()

                    if (`object` != null) {
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(MyPlanet.getTabletUsages(context))
                        `object`.add("usages", usages)
                    } else {
                        `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                    }

                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
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
                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
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

    fun uploadExamResult(listener: SuccessListener) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.processBatch(
            query = { it.where(RealmSubmission::class.java).findAll() },
            action = { r, sub ->
                if ((sub.answers?.size ?: 0) > 0) {
                    RealmSubmission.continueResultUpload(sub, apiInterface, r, context)
                }
            },
            onSuccess = {
                uploadCourseProgress()
                listener.onSuccess("Result sync completed successfully")
            },
            onError = { e ->
                e.printStackTrace()
                listener.onSuccess("Error during result sync: ${e.message}")
            }
        )
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
        val realm = getRealm()
        realm.processBatch(
            query = { it.where(RealmAchievement::class.java).findAll() },
            action = { _, sub ->
                if (sub._id?.startsWith("guest") == true) {
                    return@processBatch
                }
            }
        )
    }

    private fun uploadCourseProgress() {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.processBatch(
            query = { it.where(RealmCourseProgress::class.java).isNull("_id").findAll() },
            action = { _, sub ->
                if (sub.userId?.startsWith("guest") == true) {
                    return@processBatch
                }

                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/courses_progress",
                    RealmCourseProgress.serializeProgress(sub)
                )?.execute()?.body()
                if (obj != null) {
                    sub._id = getString("id", obj)
                    sub._rev = getString("rev", obj)
                }
            }
        )
    }

    fun uploadFeedback(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.processBatch(
            query = { it.where(RealmFeedback::class.java).findAll() },
            action = { _, feedback ->
                val res = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/feedback",
                    RealmFeedback.serializeFeedback(feedback)
                )?.execute()
                val r = res?.body()
                if (r != null) {
                    val revElement = r["rev"]
                    val idElement = r["id"]
                    if (revElement != null && idElement != null) {
                        feedback._rev = revElement.asString
                        feedback._id = idElement.asString
                    }
                }
            },
            onSuccess = { listener.onSuccess("Feedback sync completed successfully") }
        )
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        val hasPhotos = realm.where(RealmSubmitPhotos::class.java)
            .equalTo("uploaded", false)
            .findAll()
            .isNotEmpty()
        realm.processBatch(
            query = { it.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll() },
            action = { _, sub ->
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/submissions",
                    RealmSubmitPhotos.serializeRealmSubmitPhotos(sub)
                )?.execute()?.body()
                if (obj != null) {
                    val rev = getString("rev", obj)
                    val id = getString("id", obj)
                    sub.uploaded = true
                    sub._rev = rev
                    sub._id = id
                    uploadAttachment(id, rev, sub, listener!!)
                }
            },
            onSuccess = { if (!hasPhotos) listener?.onSuccess("No photos to upload") }
        )
    }

    fun uploadResource(listener: SuccessListener?) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        val userId = pref.getString("userId", "")
        val hasData = realm.where(RealmMyLibrary::class.java).isNull("_rev").findAll().isNotEmpty()
        realm.processBatch(
            query = { it.where(RealmMyLibrary::class.java).isNull("_rev").findAll() },
            action = { r, sub ->
                val user = r.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/resources",
                    RealmMyLibrary.serialize(sub, user)
                )?.execute()?.body()
                if (obj != null) {
                    val rev = getString("rev", obj)
                    val id = getString("id", obj)
                    sub._rev = rev
                    sub._id = id
                    uploadAttachment(id, rev, sub, listener!!)
                }
            },
            onSuccess = { if (!hasData) listener?.onSuccess("No resources to upload") }
        )
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
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

                            uploadAttachment(id, rev, personal, listener)
                        } catch (e: Exception) {
                            if (realm.isInTransaction) {
                                realm.cancelTransaction()
                            }
                            listener.onSuccess("Error updating personal resource: ${e.message}")
                        }
                    } else {
                        listener.onSuccess("Failed to upload personal resource: No response")
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    listener.onSuccess("Unable to upload resource: ${t.message}")
                }
            })
        } else {
            listener.onSuccess("Resource already uploaded")
        }
    }

    fun uploadTeamTask() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.processBatch(
            query = {
                val list = it.where(RealmTeamTask::class.java).findAll()
                list.filter { task -> TextUtils.isEmpty(task._id) || task.isUpdated }
            },
            action = { r, task ->
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/tasks",
                    RealmTeamTask.serialize(r, task)
                )?.execute()?.body()

                if (obj != null) {
                    val rev = getString("rev", obj)
                    val id = getString("id", obj)
                    task._rev = rev
                    task._id = id
                }
            }
        )
    }

    fun uploadSubmissions() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.processBatch(
            query = {
                it.where(RealmSubmission::class.java)
                    .equalTo("isUpdated", true).or().isEmpty("_id").findAll()
            },
            action = { r, submission ->
                val requestJson = RealmSubmission.serialize(r, submission)
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
            }
        )
    }

    fun uploadTeams() {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()

        realm.processBatch(
            query = { it.where(RealmMyTeam::class.java).equalTo("updated", true).findAll() },
            action = { _, team ->
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/teams",
                    RealmMyTeam.serialize(team)
                )?.execute()?.body()
                if (obj != null) {
                    team._rev = getString("rev", obj)
                    team.updated = false
                }
            }
        )
    }

    fun uploadUserActivities(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            listener.onSuccess("Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener.onSuccess("Skipping user activities upload for manager")
            return
        }

        val realm = getRealm()
        realm.processBatch(
            query = {
                it.where(RealmOfflineActivity::class.java)
                    .isNull("_rev")
                    .equalTo("type", "login")
                    .findAll()
            },
            action = { _, act ->
                if (act.userId?.startsWith("guest") == true) {
                    return@processBatch
                }

                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/login_activities",
                    RealmOfflineActivity.serializeLoginActivities(act, context)
                )?.execute()?.body()
                act.changeRev(obj)
            },
            onSuccess = {
                uploadTeamActivities(realm, apiInterface)
                realm.close()
                listener.onSuccess("User activities sync completed successfully")
            },
            onError = { e ->
                realm.close()
                listener.onSuccess(e.message)
            }
        )
    }

    fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
        logs.processInBatches { log ->
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))?.execute()?.body()
                    if (`object` != null) {
                        log._id = getString("id", `object`)
                        log._rev = getString("rev", `object`)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }
    }

    fun uploadRating() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.processBatch(
            query = { it.where(RealmRating::class.java).equalTo("isUpdated", true).findAll() },
            action = { _, act ->
                if (act.userId?.startsWith("guest") == true) {
                    return@processBatch
                }

                val response: Response<JsonObject>? =
                    if (TextUtils.isEmpty(act._id)) {
                        apiInterface?.postDoc(
                            Utilities.header,
                            "application/json",
                            "${Utilities.getUrl()}/ratings",
                            RealmRating.serializeRating(act)
                        )?.execute()
                    } else {
                        apiInterface?.putDoc(
                            Utilities.header,
                            "application/json",
                            "${Utilities.getUrl()}/ratings/" + act._id,
                            RealmRating.serializeRating(act)
                        )?.execute()
                    }
                if (response?.body() != null) {
                    act._id = getString("id", response.body())
                    act._rev = getString("rev", response.body())
                    act.isUpdated = false
                }
            }
        )
    }

    fun uploadNews() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.processBatch(
            query = { it.where(RealmNews::class.java).findAll() },
            action = { r, act ->
                if (act.userId?.startsWith("guest") == true) {
                    return@processBatch
                }

                val `object` = RealmNews.serializeNews(act)
                val image = act.imagesArray
                val user = r.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()

                        if (act.imageUrls != null && act.imageUrls?.isNotEmpty() == true) {
                            act.imageUrls?.chunked(5)?.forEach { imageChunk ->
                                imageChunk.forEach { imageObject ->
                                    val imgObject = Gson().fromJson(imageObject, JsonObject::class.java)
                                    val ob = createImage(user, imgObject)
                                    val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", ob)?.execute()?.body()

                                    val rev = getString("rev", response)
                                    val id = getString("id", response)
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), FileUtils.fullyReadFileToBytes(f))
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

                        act.images = Gson().toJson(image)
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
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onSuccess = { uploadNewsActivities() }
        )
    }

    fun uploadCrashLog() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        try {
            val hasLooper = Looper.myLooper() != null

            if (hasLooper) {
                realm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
                    uploadCrashLogData(realm, apiInterface)
                })
            } else {
                realm.executeTransaction { realm: Realm ->
                    uploadCrashLogData(realm, apiInterface)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadCrashLogData(realm: Realm, apiInterface: ApiInterface?) {
        val logs: RealmResults<RealmApkLog> = realm.where(RealmApkLog::class.java).isNull("_rev").findAll()

        logs.processInBatches { act ->
                try {
                    val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/apk_logs", RealmApkLog.serialize(act, context))?.execute()?.body()

                    if (o != null) {
                        act._rev = getString("rev", o)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }
    }

    fun uploadSearchActivity() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.processBatch(
            query = { it.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll() },
            action = { _, act ->
                val o = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/search_activities",
                    act.serialize()
                )?.execute()?.body()
                if (o != null) {
                    act._rev = getString("rev", o)
                }
            }
        )
    }


    fun uploadResourceActivities(type: String) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }

        realm.processBatch(
            query = {
                if (type == "sync") {
                    it.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    it.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
            },
            action = { _, act ->
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/" + db,
                    RealmResourceActivity.serializeResourceActivities(act)
                )?.execute()?.body()

                if (obj != null) {
                    act._rev = getString("rev", obj)
                    act._id = getString("id", obj)
                }
            }
        )
    }

    fun uploadCourseActivities() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.processBatch(
            query = { it.where(RealmCourseActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll() },
            action = { _, act ->
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/course_activities",
                    RealmCourseActivity.serializeSerialize(act)
                )?.execute()?.body()

                if (obj != null) {
                    act._rev = getString("rev", obj)
                    act._id = getString("id", obj)
                }
            }
        )
    }

    fun uploadMeetups() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.processBatch(
            query = { it.where(RealmMeetup::class.java).findAll() },
            action = { _, meetup ->
                val meetupJson = RealmMeetup.serialize(meetup)
                val obj = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/meetups",
                    meetupJson
                )?.execute()?.body()

                if (obj != null) {
                    meetup.meetupId = getString("id", obj)
                    meetup.meetupIdRev = getString("rev", obj)
                }
            }
        )
    }
}
