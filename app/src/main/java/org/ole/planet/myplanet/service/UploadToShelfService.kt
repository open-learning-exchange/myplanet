package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getMyMeetUpIds
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseIds
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmMyHealthPojo.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibIds
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.removedIds
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.RetryUtils
import org.ole.planet.myplanet.utilities.SecurePrefs
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities

@Singleton
class UploadToShelfService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences,
    private val apiInterface: ApiInterface,
    @ApplicationScope private val serviceScope: CoroutineScope
) {

    fun uploadUserData(listener: SuccessListener) {
        serviceScope.launch {
            val userModels = withContext(Dispatchers.IO) {
                dbService.withRealm { realm ->
                    val users = realm.where(RealmUserModel::class.java)
                        .isEmpty("_id").or().equalTo("isUpdated", true)
                        .findAll()
                        .take(100)
                    realm.copyFromRealm(users)
                }
            }

            if (userModels.isEmpty()) {
                uploadToShelf(listener)
                return@launch
            }

            val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
            userModels.forEach { model ->
                try {
                    val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
                    val userExists = checkIfUserExists(header, model)

                    if (!userExists) {
                        uploadNewUser(model)
                    } else if (model.isUpdated) {
                        updateExistingUser(header, model)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            uploadToShelf(listener)
        }
    }

    fun uploadSingleUserData(userName: String?, listener: SuccessListener) {
        serviceScope.launch {
            val userModel = withContext(Dispatchers.IO) {
                dbService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java)
                        .equalTo("name", userName)
                        .findFirst()
                        ?.let { realm.copyFromRealm(it) }
                }
            }

            if (userModel != null) {
                try {
                    val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                    val header = "Basic ${Base64.encodeToString(("${userModel.name}:${password}").toByteArray(), Base64.NO_WRAP)}"

                    val userExists = checkIfUserExists(header, userModel)

                    if (!userExists) {
                        uploadNewUser(userModel)
                    } else if (userModel.isUpdated) {
                        updateExistingUser(header, userModel)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            uploadSingleUserToShelf(userName, listener)
        }
    }

    private suspend fun checkIfUserExists(header: String, model: RealmUserModel): Boolean {
        return try {
            val res = apiInterface.getJsonObjectSuspended(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")
            res.body() != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun uploadNewUser(model: RealmUserModel) {
        try {
            val obj = model.serialize()
            val createResponse = apiInterface.putDocSuspend(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)

            if (createResponse.isSuccessful) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString
                model._id = id
                model._rev = rev
                updateRealm(model)
                processUserAfterCreation(model, obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processUserAfterCreation(model: RealmUserModel, obj: JsonObject) {
        try {
            val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val fetchDataResponse = apiInterface.getJsonObjectSuspended(header, "${replacedUrl(model)}/_users/${model._id}")
            if (fetchDataResponse.isSuccessful) {
                model.password_scheme = getString("password_scheme", fetchDataResponse.body())
                model.derived_key = getString("derived_key", fetchDataResponse.body())
                model.salt = getString("salt", fetchDataResponse.body())
                model.iterations = getString("iterations", fetchDataResponse.body())

                updateRealm(model)

                if (saveKeyIv(model, obj)) {
                    updateHealthData(model)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun updateExistingUser(header: String, model: RealmUserModel) {
        try {
            val latestDocResponse = apiInterface.getJsonObjectSuspended(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")

            if (latestDocResponse.isSuccessful) {
                val latestRev = latestDocResponse.body()?.get("_rev")?.asString
                val obj = model.serialize()
                val objMap = obj.entrySet().associate { (key, value) -> key to value }
                val mutableObj = mutableMapOf<String, Any>().apply { putAll(objMap) }
                latestRev?.let { rev -> mutableObj["_rev"] = rev as Any }

                val gson = Gson()
                val jsonElement = gson.toJsonTree(mutableObj)
                val jsonObject = jsonElement.asJsonObject

                val updateResponse = apiInterface.putDocSuspend(header, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", jsonObject)

                if (updateResponse.isSuccessful) {
                    val updatedRev = updateResponse.body()?.get("rev")?.asString
                    model._rev = updatedRev
                    model.isUpdated = false
                    updateRealm(model)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun replacedUrl(model: RealmUserModel): String {
        val url = UrlUtils.getUrl()
        val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
        val replacedUrl = url.replaceFirst("[^:]+:[^@]+@".toRegex(), "${model.name}:${password}@")
        val protocolIndex = url.indexOf("://")
        val protocol = url.substring(0, protocolIndex)
        return "$protocol://$replacedUrl"
    }

    private suspend fun updateHealthData(model: RealmUserModel) {
        withContext(Dispatchers.IO) {
            dbService.withRealm { realm ->
                realm.executeTransaction { r ->
                    val list = r.where(RealmMyHealthPojo::class.java).equalTo("_id", model.id).findAll()
                    for (p in list) {
                        p.userId = model._id
                    }
                }
            }
        }
    }

    private suspend fun updateRealm(model: RealmUserModel) {
        withContext(Dispatchers.IO) {
            dbService.withRealm { realm ->
                realm.executeTransaction { r ->
                    r.copyToRealmOrUpdate(model)
                }
            }
        }
    }

    @Throws(IOException::class)
    suspend fun saveKeyIv(model: RealmUserModel, obj: JsonObject, unusedApiInterface: ApiInterface? = null): Boolean {
        val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
        val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
        val ob = JsonObject()
        var keyString = generateKey()
        var iv: String? = generateIv()
        if (!TextUtils.isEmpty(model.iv)) {
            iv = model.iv
        }
        if (!TextUtils.isEmpty(model.key)) {
            keyString = model.key
        }
        ob.addProperty("key", keyString)
        ob.addProperty("iv", iv)
        ob.addProperty("createdOn", Date().time)
        val maxAttempts = 3
        val retryDelayMs = 2000L

        val response = RetryUtils.retry(
            maxAttempts = maxAttempts,
            delayMs = retryDelayMs,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful || resp.body() == null }
        ) {
            apiInterface.postDocSuspend(header, "application/json", "${UrlUtils.getUrl()}/$table", ob)
        }

        if (response?.isSuccessful == true && response.body() != null) {
            model.key = keyString
            model.iv = iv
            updateRealm(model)
        } else {
            val errorMessage = "Failed to save key/IV after $maxAttempts attempts"
            throw IOException(errorMessage)
        }

        changeUserSecurity(model, obj)
        return true
    }

    fun uploadHealth() {
        serviceScope.launch {
            val myHealths = withContext(Dispatchers.IO) {
                dbService.withRealm { realm ->
                    val list = realm.where(RealmMyHealthPojo::class.java)
                        .equalTo("isUpdated", true)
                        .notEqualTo("userId", "")
                        .findAll()
                    realm.copyFromRealm(list)
                }
            }

            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/health", serialize(pojo))

                    if (res.isSuccessful && res.body()?.has("id") == true) {
                        pojo._rev = res.body()?.get("rev")?.asString
                        pojo.isUpdated = false
                        withContext(Dispatchers.IO) {
                            dbService.withRealm { realm ->
                                realm.executeTransaction { r ->
                                    r.copyToRealmOrUpdate(pojo)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun uploadSingleUserHealth(userId: String?, listener: SuccessListener?) {
        serviceScope.launch {
            if (userId.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Health data uploaded: userId empty")
                }
                return@launch
            }

            val myHealths = withContext(Dispatchers.IO) {
                dbService.withRealm { realm ->
                    val list = realm.where(RealmMyHealthPojo::class.java)
                        .equalTo("isUpdated", true)
                        .equalTo("userId", userId)
                        .findAll()
                    realm.copyFromRealm(list)
                }
            }

            var lastError: String? = null
            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/health",
                        serialize(pojo)
                    )

                    if (res.isSuccessful && res.body()?.has("id") == true) {
                        pojo._rev = res.body()?.get("rev")?.asString
                        pojo.isUpdated = false
                        withContext(Dispatchers.IO) {
                            dbService.withRealm { realm ->
                                realm.executeTransaction { r ->
                                    r.copyToRealmOrUpdate(pojo)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    lastError = e.localizedMessage
                }
            }

            withContext(Dispatchers.Main) {
                if (lastError != null) {
                    listener?.onSuccess("Error uploading health data for user $userId: $lastError")
                } else {
                    listener?.onSuccess("Health data for user $userId uploaded successfully")
                }
            }
        }
    }

    private suspend fun uploadToShelf(listener: SuccessListener) {
        val unmanagedUsers = withContext(Dispatchers.IO) {
            dbService.withRealm { realm ->
                val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()
                realm.copyFromRealm(users)
            }
        }

        if (unmanagedUsers.isEmpty()) {
            withContext(Dispatchers.Main) {
                listener.onSuccess("Sync with server completed successfully")
            }
            return
        }

        var lastError: String? = null
        withContext(Dispatchers.IO) {
            // Need a realm instance for getShelfData to query related items
             unmanagedUsers.forEach { model ->
                try {
                    if (model.id?.startsWith("guest") == true) return@forEach
                    val jsonDoc = apiInterface.getJsonObjectSuspended(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/${model._id}").body()
                    val `object` = dbService.withRealm { backgroundRealm ->
                        getShelfData(backgroundRealm, model.id, jsonDoc)
                    }
                    `object`.addProperty("_rev", getString("_rev", jsonDoc))
                    apiInterface.putDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}", `object`)
                } catch (e: Exception) {
                    e.printStackTrace()
                    lastError = e.localizedMessage
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (lastError != null) {
                listener.onSuccess("Unable to update documents: $lastError")
            } else {
                listener.onSuccess("Sync with server completed successfully")
            }
        }
    }

    private suspend fun uploadSingleUserToShelf(userName: String?, listener: SuccessListener) {
        val model = withContext(Dispatchers.IO) {
            dbService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("name", userName)
                    .isNotEmpty("_id")
                    .findFirst()
                    ?.let { realm.copyFromRealm(it) }
            }
        }

        if (model != null) {
            try {
                if (model.id?.startsWith("guest") == true) {
                    withContext(Dispatchers.Main) {
                        listener.onSuccess("Single user shelf sync completed successfully")
                    }
                    return
                }

                withContext(Dispatchers.IO) {
                    val shelfUrl = "${UrlUtils.getUrl()}/shelf/${model._id}"
                    val jsonDoc = apiInterface.getJsonObjectSuspended(UrlUtils.header, shelfUrl).body()
                    val shelfObject = dbService.withRealm { realm ->
                        getShelfData(realm, model.id, jsonDoc)
                    }
                    shelfObject.addProperty("_rev", getString("_rev", jsonDoc))

                    val targetUrl = "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}"
                    apiInterface.putDocSuspend(UrlUtils.header, "application/json", targetUrl, shelfObject)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Unable to update document: ${e.localizedMessage}")
                }
                return
            }
        }

        withContext(Dispatchers.Main) {
            listener.onSuccess("Single user shelf sync completed successfully")
        }
    }

    private fun getShelfData(realm: Realm?, userId: String?, jsonDoc: JsonObject?): JsonObject {
        val myLibs = getMyLibIds(realm, userId)
        val myCourses = getMyCourseIds(realm, userId)
        val myMeetups = getMyMeetUpIds(realm, userId)
        val removedResources = listOf(*removedIds(realm, "resources", userId))
        val removedCourses = listOf(*removedIds(realm, "courses", userId))
        val mergedResourceIds = mergeJsonArray(myLibs, getJsonArray("resourceIds", jsonDoc), removedResources)
        val mergedCourseIds = mergeJsonArray(myCourses, getJsonArray("courseIds", jsonDoc), removedCourses)
        val `object` = JsonObject()
        `object`.addProperty("_id", sharedPreferences.getString("userId", ""))
        `object`.add("meetupIds", mergeJsonArray(myMeetups, getJsonArray("meetupIds", jsonDoc), removedResources))
        `object`.add("resourceIds", mergedResourceIds)
        `object`.add("courseIds", mergedCourseIds)
        return `object`
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

    private suspend fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
        val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
        val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"

        try {
            val response = apiInterface.getJsonObjectSuspended(header, "${UrlUtils.getUrl()}/${table}/_security")
            if (response.body() != null) {
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
                apiInterface.putDocSuspend(header, "application/json", "${UrlUtils.getUrl()}/${table}/_security", jsonObject)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
