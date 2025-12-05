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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
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
import retrofit2.Response

@Singleton
class UploadToShelfService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences
) {
    fun uploadUserData(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                val userModels = dbService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java)
                        .isEmpty("_id").or().equalTo("isUpdated", true)
                        .findAll()
                        .take(100)
                        .let { realm.copyFromRealm(it) }
                }

                if (userModels.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        uploadToShelf(listener)
                    }
                    return@launch
                }

                val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                val updatedModels = userModels.mapNotNull { model ->
                    try {
                        val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
                        val userExists = checkIfUserExists(apiInterface, header, model)

                        if (!userExists) {
                            uploadNewUser(apiInterface, model)
                        } else if (model.isUpdated) {
                            updateExistingUser(apiInterface, header, model)
                        } else {
                            null
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                }

                if (updatedModels.isNotEmpty()) {
                    dbService.withRealm { realm ->
                        updatedModels.forEach { model ->
                            realm.insertOrUpdate(model)
                            updateHealthData(realm, model)
                        }
                    }
                }


                withContext(Dispatchers.Main) {
                    uploadToShelf(listener)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    fun uploadSingleUserData(userName: String?, listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                val userModel = dbService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java)
                        .equalTo("name", userName)
                        .findFirst()
                        ?.let { realm.copyFromRealm(it) }
                }

                if (userModel != null) {
                    val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                    val header = "Basic ${Base64.encodeToString(("${userModel.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
                    val userExists = checkIfUserExists(apiInterface, header, userModel)
                    val updatedModel = if (!userExists) {
                        uploadNewUser(apiInterface, userModel)
                    } else if (userModel.isUpdated) {
                        updateExistingUser(apiInterface, header, userModel)
                    } else {
                        null
                    }
                    if (updatedModel != null) {
                        dbService.withRealm { realm ->
                            realm.insertOrUpdate(updatedModel)
                            updateHealthData(realm, updatedModel)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    uploadSingleUserToShelf(userName, listener)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun checkIfUserExists(apiInterface: ApiInterface?, header: String, model: RealmUserModel): Boolean {
        return try {
            val res = withContext(Dispatchers.IO) {
                apiInterface?.getJsonObject(
                    header,
                    "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}"
                )?.execute()
            }
            res?.body() != null
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun uploadNewUser(apiInterface: ApiInterface?, model: RealmUserModel): RealmUserModel? {
        return try {
            val obj = model.serialize()
            val createResponse = withContext(Dispatchers.IO) {
                apiInterface?.putDoc(
                    null,
                    "application/json",
                    "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}",
                    obj
                )?.execute()
            }

            if (createResponse?.isSuccessful == true) {
                model._id = createResponse.body()?.get("id")?.asString
                model._rev = createResponse.body()?.get("rev")?.asString
                processUserAfterCreation(apiInterface, model, obj)
                model
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun processUserAfterCreation(apiInterface: ApiInterface?, model: RealmUserModel, obj: JsonObject) {
        try {
            val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val fetchDataResponse = withContext(Dispatchers.IO) {
                apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/${model._id}")?.execute()
            }
            if (fetchDataResponse?.isSuccessful == true) {
                model.password_scheme = getString("password_scheme", fetchDataResponse.body())
                model.derived_key = getString("derived_key", fetchDataResponse.body())
                model.salt = getString("salt", fetchDataResponse.body())
                model.iterations = getString("iterations", fetchDataResponse.body())
                saveKeyIv(apiInterface, model, obj)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private suspend fun updateExistingUser(apiInterface: ApiInterface?, header: String, model: RealmUserModel): RealmUserModel? {
        return try {
            val latestDocResponse = withContext(Dispatchers.IO) {
                apiInterface?.getJsonObject(
                    header,
                    "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}"
                )?.execute()
            }

            if (latestDocResponse?.isSuccessful == true) {
                val latestRev = latestDocResponse.body()?.get("_rev")?.asString
                val obj = model.serialize()
                val objMap = obj.entrySet().associate { (key, value) -> key to value }
                val mutableObj = mutableMapOf<String, Any>().apply { putAll(objMap) }
                latestRev?.let { rev -> mutableObj["_rev"] = rev as Any }

                val gson = Gson()
                val jsonElement = gson.toJsonTree(mutableObj)
                val jsonObject = jsonElement.asJsonObject

                val updateResponse = withContext(Dispatchers.IO) {
                    apiInterface?.putDoc(
                        header,
                        "application/json",
                        "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}",
                        jsonObject
                    )?.execute()
                }

                if (updateResponse?.isSuccessful == true) {
                    model._rev = updateResponse.body()?.get("rev")?.asString
                    model.isUpdated = false
                    model
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
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

    private fun updateHealthData(realm: Realm, model: RealmUserModel) {
        val list: List<RealmMyHealthPojo> = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", model.id).findAll()
        for (p in list) {
            p.userId = model._id
        }
    }

    suspend fun saveKeyIv(apiInterface: ApiInterface?, model: RealmUserModel, obj: JsonObject) {
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

        val response = withContext(Dispatchers.IO) {
            RetryUtils.retry(
                maxAttempts = maxAttempts,
                delayMs = retryDelayMs,
                shouldRetry = { resp -> resp == null || !resp.isSuccessful || resp.body() == null }
            ) {
                apiInterface?.postDocSuspend(header, "application/json", "${UrlUtils.getUrl()}/$table", ob)
            }
        }

        if (response?.isSuccessful == true && response.body() != null) {
            model.key = keyString
            model.iv = iv
            withContext(Dispatchers.IO) {
                changeUserSecurity(model, obj)
            }
        } else {
            val errorMessage = "Failed to save key/IV after $maxAttempts attempts"
            throw IOException(errorMessage)
        }
    }

    fun uploadHealth() {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            val myHealths = dbService.withRealm { realm ->
                realm.where(RealmMyHealthPojo::class.java)
                    .equalTo("isUpdated", true)
                    .notEqualTo("userId", "")
                    .findAll()
                    .let { realm.copyFromRealm(it) }
            }
            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface?.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/health",
                        serialize(pojo)
                    )?.execute()
                    if (res?.body() != null && res.body()?.has("id") == true) {
                        dbService.withRealm { realm ->
                            val p = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", pojo._id).findFirst()
                            p?.let {
                                it._rev = res.body()?.get("rev")?.asString
                                it.isUpdated = false
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
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            if (userId.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("User ID is null or empty")
                }
                return@launch
            }
            try {
                val myHealths = dbService.withRealm { realm ->
                    realm.where(RealmMyHealthPojo::class.java)
                        .equalTo("isUpdated", true)
                        .equalTo("userId", userId)
                        .findAll()
                        .let { realm.copyFromRealm(it) }
                }
                myHealths.forEach { pojo ->
                    val res = apiInterface?.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/health",
                        serialize(pojo)
                    )?.execute()
                    if (res?.body() != null && res.body()?.has("id") == true) {
                        dbService.withRealm { realm ->
                            val p = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", pojo._id).findFirst()
                            p?.let {
                                it._rev = res.body()?.get("rev")?.asString
                                it.isUpdated = false
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Health data for user $userId uploaded successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Error uploading health data for user $userId: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadToShelf(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            val unmanagedUsers = dbService.realmInstance.use { realm ->
                realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll().let {
                    realm.copyFromRealm(it)
                }
            }

            if (unmanagedUsers.isEmpty()) {
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
                return@launch
            }

            try {
                unmanagedUsers.forEach { model ->
                    if (model.id?.startsWith("guest") == true) return@forEach
                    try {
                        val jsonDoc = apiInterface?.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/${model._id}")?.execute()?.body()
                        val shelfData = dbService.realmInstance.use { backgroundRealm ->
                            getShelfData(backgroundRealm, model.id, jsonDoc)
                        }
                        shelfData.addProperty("_rev", getString("_rev", jsonDoc))
                        apiInterface?.putDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}",
                            shelfData
                        )?.execute()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Unable to update documents: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadSingleUserToShelf(userName: String?, listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                val model = dbService.realmInstance.use { realm ->
                    realm.where(RealmUserModel::class.java)
                        .equalTo("name", userName)
                        .isNotEmpty("_id")
                        .findFirst()
                        ?.let { realm.copyFromRealm(it) }
                }

                if (model != null) {
                    if (model.id?.startsWith("guest") != true) {
                        val shelfUrl = "${UrlUtils.getUrl()}/shelf/${model._id}"
                        val jsonDoc = apiInterface?.getJsonObject(UrlUtils.header, shelfUrl)?.execute()?.body()
                        val shelfObject = dbService.realmInstance.use { realm ->
                            getShelfData(realm, model.id, jsonDoc)
                        }
                        shelfObject.addProperty("_rev", getString("_rev", jsonDoc))

                        val targetUrl = "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}"
                        apiInterface?.putDoc(UrlUtils.header, "application/json", targetUrl, shelfObject)?.execute()
                    }
                }
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Single user shelf sync completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Unable to update document: ${e.localizedMessage}")
                }
            }
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

    companion object {
        private fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
            val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
            val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
            val apiInterface = client?.create(ApiInterface::class.java)
            try {
                val response: Response<JsonObject?>? = apiInterface?.getJsonObject(header, "${UrlUtils.getUrl()}/${table}/_security")?.execute()
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
                    apiInterface.putDoc(header, "application/json", "${UrlUtils.getUrl()}/${table}/_security", jsonObject).execute()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
