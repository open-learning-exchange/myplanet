package org.ole.planet.myplanet.service

import android.content.*
import android.text.TextUtils
import android.util.Base64
import com.google.gson.*
import io.realm.kotlin.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.*
import retrofit2.Response
import java.io.IOException
import java.util.Date

class UploadToShelfService(context: Context) {
    private val dbService: DatabaseService = DatabaseService()
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val realm: Realm by lazy { dbService.realmInstance }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiInterface = client?.create(ApiInterface::class.java)

    fun uploadUserData(listener: SuccessListener) {
        scope.launch {
            try {
                realm.write {
                    val userModels = query<RealmUserModel>(RealmUserModel::class).query("_id == '' OR isUpdated == true").find()
                    for (model in userModels) {
                        try {
                            val password = sharedPreferences.getString("loginUserPassword", "")
                            val header = "Basic ${Base64.encodeToString(("${ model.name }:${ password }").toByteArray(), Base64.NO_WRAP)}"
                            val res = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")?.execute()

                            if (res?.body() == null) {
                                val obj = model.serialize()
                                val createResponse = apiInterface?.putDoc(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)?.execute()

                                if (createResponse?.isSuccessful == true) {
                                    val id = createResponse.body()?.get("id")?.asString
                                    val rev = createResponse.body()?.get("rev")?.asString
                                    findLatest(model)?.apply {
                                        _id = id
                                        _rev = rev
                                    }

                                    val fetchDataResponse = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/$id").execute()
                                    if (fetchDataResponse.isSuccessful) {
                                        findLatest(model)?.apply {
                                            password_scheme = JsonUtils.getString("password_scheme", fetchDataResponse.body())
                                            derived_key = JsonUtils.getString("derived_key", fetchDataResponse.body())
                                            salt = JsonUtils.getString("salt", fetchDataResponse.body())
                                            iterations = JsonUtils.getString("iterations", fetchDataResponse.body())
                                        }
                                        if (saveKeyIv(apiInterface, model, obj)) {
                                            updateHealthData(model)
                                        }
                                    }
                                }
                            } else if (model.isUpdated) {
                                try {
                                    val latestDocResponse = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}").execute()

                                    if (latestDocResponse.isSuccessful) {
                                        val latestRev = latestDocResponse.body()?.get("_rev")?.asString
                                        val obj = model.serialize()
                                        val objMap = obj.entrySet().associate { (key, value) -> key to value }
                                        val mutableObj = mutableMapOf<String, Any>().apply { putAll(objMap) }
                                        latestRev?.let { rev -> mutableObj["_rev"] = rev as Any }
                                        val gson = Gson()
                                        val jsonElement = gson.toJsonTree(mutableObj)
                                        val jsonObject = jsonElement.asJsonObject

                                        val updateResponse = apiInterface.putDoc(header, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", jsonObject).execute()

                                        if (updateResponse.isSuccessful) {
                                            findLatest(model)?.apply {
                                                _rev = updateResponse.body()?.get("rev")?.asString
                                                isUpdated = false
                                            }
                                        }
                                    }
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }
                            } else {
                                Utilities.toast(MainApplication.context, "User ${model.name} already exists")
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
                uploadToShelf(listener)
            } catch (e: Exception) {
                e.printStackTrace()
                uploadToShelf(listener)
            }
        }
    }

    private fun replacedUrl(model: RealmUserModel): String {
        val url = Utilities.getUrl()
        val password = sharedPreferences.getString("loginUserPassword", "")
        val replacedUrl = url.replaceFirst("[^:]+:[^@]+@".toRegex(), "${model.name}:${password}@")
        val protocolIndex = url.indexOf("://") // Find the index of "://"
        val protocol = url.substring(0, protocolIndex) // Extract the protocol (http or https)
        return "$protocol://$replacedUrl" // Append the protocol to the modified URL
    }

    private fun updateHealthData(model: RealmUserModel) {
        scope.launch {
            realm.write {
                query<RealmMyHealthPojo>(RealmMyHealthPojo::class)
                    .query("_id == $0", model.id).find().forEach { healthPojo ->
                        findLatest(healthPojo)?.apply {
                            userId = model._id
                        }
                    }
            }
        }
    }

    @Throws(IOException::class)
    fun saveKeyIv(apiInterface: ApiInterface?, model: RealmUserModel, obj: JsonObject): Boolean {
        val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
        val header = "Basic ${Base64.encodeToString(("${ obj["name"].asString }:${ obj["password"].asString }").toByteArray(), Base64.NO_WRAP)}"
        val ob = JsonObject()
        var keyString = AndroidDecrypter.generateKey()
        var iv: String? = AndroidDecrypter.generateIv()
        if (!TextUtils.isEmpty(model.iv)) {
            iv = model.iv
        }
        if (!TextUtils.isEmpty(model.key)) {
            keyString = model.key
        }
        ob.addProperty("key", keyString)
        ob.addProperty("iv", iv)
        ob.addProperty("createdOn", Date().time)
        var success = false
        while (!success) {
            val response: Response<JsonObject>? = apiInterface?.postDoc(header, "application/json", Utilities.getUrl() + "/" + table, ob)?.execute()
            if (response?.body() != null) {
                model.key = keyString
                model.iv = iv
                success = true
            } else {
                success = false
            }
        }
        changeUserSecurity(model, obj)
        return true
    }

    fun uploadHealth() {
        scope.launch {
            realm.write {
                val myHealths = query<RealmMyHealthPojo>(RealmMyHealthPojo::class).query("isUpdated == true AND userId != ''").find()

                for (pojo in myHealths) {
                    try {
                        val res = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/health", RealmMyHealthPojo.serialize(pojo))?.execute()

                        if (res?.body() != null && res.body()?.has("id") == true) {
                            findLatest(pojo)?.apply {
                                _rev = res.body()!!["rev"].asString
                                isUpdated = false
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun uploadToShelf(listener: SuccessListener) {
        scope.launch {
            try {
                realm.write {
                    val users = query<RealmUserModel>(RealmUserModel::class).query("_id != ''").find()
                    for (model in users) {
                        try {
                            if (model.id.startsWith("guest") == true) {
                                continue
                            }

                            val jsonDoc = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${model._id}")?.execute()?.body()

                            val `object` = getShelfData(model.id, jsonDoc)
                            val d = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${model.id}")?.execute()?.body()

                            `object`.addProperty("_rev", JsonUtils.getString("_rev", d))
                            apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}", `object`)?.execute()?.body()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Unable to update documents.")
                }
            }
        }
    }

    private fun getShelfData(userId: String, jsonDoc: JsonObject?): JsonObject {
        val myLibs = RealmMyLibrary.getMyLibIds(realm, userId)
        val myCourses = RealmMyCourse.getMyCourseIds(realm, userId)
        val myMeetups = RealmMeetup.getMyMeetUpIds(realm, userId)
        val removedResources = listOf(*RealmRemovedLog.removedIds(realm, "resources", userId))
        val removedCourses = listOf(*RealmRemovedLog.removedIds(realm, "courses", userId))
        val mergedResourceIds = mergeJsonArray(myLibs, JsonUtils.getJsonArray("resourceIds", jsonDoc), removedResources)
        val mergedCourseIds = mergeJsonArray(myCourses, JsonUtils.getJsonArray("courseIds", jsonDoc), removedCourses)

        return JsonObject().apply {
            addProperty("_id", sharedPreferences.getString("userId", ""))
            add("meetupIds", mergeJsonArray(myMeetups, JsonUtils.getJsonArray("meetupIds", jsonDoc), removedResources))
            add("resourceIds", mergedResourceIds)
            add("courseIds", mergedCourseIds)
        }
    }

    private fun mergeJsonArray(array1: JsonArray?, array2: JsonArray, removedIds: List<String>): JsonArray {
        val array = JsonArray()
        array.addAll(array1)
        for (e in array2) {
            if (!array.contains(e) && !removedIds.contains(e.asString)) {
                array.add(e)
            }
        }
        return array
    }

    companion object {
        var instance: UploadToShelfService? = null
            get() {
                if (field == null) {
                    field = UploadToShelfService(MainApplication.context)
                }
                return field
            }
            private set

        private fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
            val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
            val header = "Basic ${Base64.encodeToString(("${ obj["name"].asString }:" + "${ obj["password"].asString }").toByteArray(), Base64.NO_WRAP)}"
            val apiInterface = client?.create(ApiInterface::class.java)
            val response: Response<JsonObject?>?
            try {
                response = apiInterface?.getJsonObject(header, "${Utilities.getUrl()}/$table/_security")?.execute()
                if (response?.body() != null) {
                    val jsonObject = response.body()
                    val members = jsonObject?.getAsJsonObject("members")
                    val rolesArray: JsonArray = if (members?.has("roles") == true) {
                        members.getAsJsonArray("roles")
                    } else {
                        JsonArray()
                    }
                    rolesArray.add("health")
                    members?.add("roles", rolesArray)
                    jsonObject?.add("members", members)
                    apiInterface.putDoc(header, "application/json", "${Utilities.getUrl()}/$table/_security", jsonObject).execute()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
