package org.ole.planet.myplanet.services

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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiClient.client
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmHealthExamination.Companion.serialize
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getMyMeetUpIds
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseIds
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibIds
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.removedIds
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utils.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.RetryUtils
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@Singleton
class UploadToShelfService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences
) {
    lateinit var mRealm: Realm

    fun uploadUserData(listener: OnSuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                val userModels = dbService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java)
                        .isEmpty("_id").or().equalTo("isUpdated", true)
                        .findAll()
                        .take(100)
                        .map { realm.copyFromRealm(it) }
                }

                if (userModels.isEmpty()) return@launch

                val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                userModels.forEach { model ->
                    try {
                        val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
                        val userExists = checkIfUserExists(apiInterface, header, model)

                        if (!userExists) {
                            uploadNewUser(apiInterface, model)
                        } else if (model.isUpdated) {
                            updateExistingUser(apiInterface, header, model)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                uploadToShelf(object : OnSuccessListener {
                    override fun onSuccess(success: String?) {
                        MainScope().launch {
                            listener.onSuccess(success)
                        }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    fun uploadSingleUserData(userName: String?, listener: OnSuccessListener) {
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
                    try {
                        val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                        val header = "Basic ${Base64.encodeToString(("${userModel.name}:${password}").toByteArray(), Base64.NO_WRAP)}"

                        val userExists = checkIfUserExists(apiInterface, header, userModel)

                        if (!userExists) {
                            uploadNewUser(apiInterface, userModel)
                        } else if (userModel.isUpdated) {
                            updateExistingUser(apiInterface, header, userModel)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                uploadSingleUserToShelf(userName, listener)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun checkIfUserExists(apiInterface: ApiInterface?, header: String, model: RealmUserModel): Boolean {
        try {
            val res = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")
            val exists = res?.body() != null
            return exists
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun uploadNewUser(apiInterface: ApiInterface?, model: RealmUserModel) {
        try {
            val obj = model.serialize()
            val createResponse = apiInterface?.putDoc(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)

            if (createResponse?.isSuccessful == true) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString
                model._id = id
                model._rev = rev
                
                // Persist _id and _rev to database
                dbService.executeTransactionAsync { realm ->
                    val managedModel = realm.where(RealmUserModel::class.java).equalTo("id", model.id).findFirst()
                    if (managedModel != null) {
                        managedModel._id = id
                        managedModel._rev = rev
                    } else {
                        android.util.Log.e("UploadToShelfService", "Failed to find user model with id: ${model.id} for persisting _id and _rev")
                    }
                }
                
                processUserAfterCreation(apiInterface, model, obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processUserAfterCreation(apiInterface: ApiInterface?, model: RealmUserModel, obj: JsonObject) {
        try {
            val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val fetchDataResponse = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/${model._id}")

            if (fetchDataResponse?.isSuccessful == true) {
                model.password_scheme = getString("password_scheme", fetchDataResponse.body())
                model.derived_key = getString("derived_key", fetchDataResponse.body())
                model.salt = getString("salt", fetchDataResponse.body())
                model.iterations = getString("iterations", fetchDataResponse.body())
                saveKeyIv(apiInterface, model, obj)

                dbService.executeTransactionAsync { realm ->
                    updateHealthData(realm, model)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun updateExistingUser(apiInterface: ApiInterface?, header: String, model: RealmUserModel) {
        try {
            val latestDocResponse = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")

            if (latestDocResponse?.isSuccessful == true) {
                val latestRev = latestDocResponse.body()?.get("_rev")?.asString
                val obj = model.serialize()
                val objMap = obj.entrySet().associate { (key, value) -> key to value }
                val mutableObj = mutableMapOf<String, Any>().apply { putAll(objMap) }
                latestRev?.let { rev -> mutableObj["_rev"] = rev as Any }

                val gson = Gson()
                val jsonElement = gson.toJsonTree(mutableObj)
                val jsonObject = jsonElement.asJsonObject

                val updateResponse = apiInterface.putDoc(header, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", jsonObject)

                if (updateResponse.isSuccessful) {
                    val updatedRev = updateResponse.body()?.get("rev")?.asString
                    dbService.executeTransactionAsync { realm ->
                        val managedModel = realm.where(RealmUserModel::class.java).equalTo("id", model.id).findFirst()
                        managedModel?._rev = updatedRev
                        managedModel?.isUpdated = false
                    }
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

    private fun updateHealthData(realm: Realm, model: RealmUserModel) {
        val list: List<RealmHealthExamination> = realm.where(RealmHealthExamination::class.java).equalTo("_id", model.id).findAll()
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
        val dbUrl = "${UrlUtils.getUrl()}/$table"
        
        withContext(Dispatchers.IO) {
            try {
                apiInterface?.putDoc(header, "application/json", dbUrl, JsonObject())
            } catch (e: Exception) {
                null
            }
        }

        val response = withContext(Dispatchers.IO) {
            RetryUtils.retry(
                maxAttempts = maxAttempts,
                delayMs = retryDelayMs,
                shouldRetry = { resp -> resp == null || !resp.isSuccessful || resp.body() == null }
            ) {
                apiInterface?.postDoc(header, "application/json", "${UrlUtils.getUrl()}/$table", ob)
            }
        }

        if (response?.isSuccessful == true && response.body() != null) {
            changeUserSecurity(model, obj)

            dbService.executeTransactionAsync { realm ->
                val managedModel = realm.where(RealmUserModel::class.java).equalTo("id", model.id).findFirst()
                managedModel?.key = keyString
                managedModel?.iv = iv
            }
        } else {
            throw IOException("Failed to save key/IV after $maxAttempts attempts")
        }
    }

    fun uploadHealth() {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            val myHealths = dbService.withRealm { realm ->
                realm.where(RealmHealthExamination::class.java)
                    .equalTo("isUpdated", true)
                    .notEqualTo("userId", "")
                    .findAll()
                    .map { realm.copyFromRealm(it) }
            }

            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/health", serialize(pojo))

                    if (res?.body() != null && res.body()?.has("id") == true) {
                        val rev = res.body()?.get("rev")?.asString
                        dbService.executeTransactionAsync { realm ->
                            val managedPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", pojo._id).findFirst()
                            managedPojo?._rev = rev
                            managedPojo?.isUpdated = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun uploadSingleUserHealth(userId: String?, listener: OnSuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                if (userId.isNullOrEmpty()) return@launch

                val myHealths = dbService.withRealm { realm ->
                    realm.where(RealmHealthExamination::class.java)
                        .equalTo("isUpdated", true)
                        .equalTo("userId", userId)
                        .findAll()
                        .map { realm.copyFromRealm(it) }
                }

                myHealths.forEach { pojo ->
                    try {
                        val res = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/health",
                            serialize(pojo)
                        )

                        if (res?.body() != null && res.body()?.has("id") == true) {
                            val rev = res.body()?.get("rev")?.asString
                            dbService.executeTransactionAsync { realm ->
                                val managedPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", pojo._id).findFirst()
                                managedPojo?._rev = rev
                                managedPojo?.isUpdated = false
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Health data for user $userId uploaded successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener?.onSuccess("Error uploading health data for user $userId: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadToShelf(listener: OnSuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            val unmanagedUsers = dbService.withRealm { realm ->
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
                        val jsonDoc = apiInterface?.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/${model._id}")?.body()
                        val shelfData = dbService.withRealm { backgroundRealm ->
                            getShelfData(backgroundRealm, model.id, jsonDoc)
                        }
                        shelfData.addProperty("_rev", getString("_rev", jsonDoc))
                        apiInterface?.putDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}",
                            shelfData
                        )
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

    private fun uploadSingleUserToShelf(userName: String?, listener: OnSuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                val model = dbService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java)
                        .equalTo("name", userName)
                        .isNotEmpty("_id")
                        .findFirst()
                        ?.let { realm.copyFromRealm(it) }
                }

                if (model != null) {
                    if (model.id?.startsWith("guest") != true) {
                        val shelfUrl = "${UrlUtils.getUrl()}/shelf/${model._id}"
                        val jsonDoc = apiInterface?.getJsonObject(UrlUtils.header, shelfUrl)?.body()
                        val shelfObject = dbService.withRealm { realm ->
                            getShelfData(realm, model.id, jsonDoc)
                        }
                        shelfObject.addProperty("_rev", getString("_rev", jsonDoc))

                        val targetUrl = "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}"
                        apiInterface?.putDoc(UrlUtils.header, "application/json", targetUrl, shelfObject)
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
        private suspend fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
            val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
            val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
            val apiInterface = client?.create(ApiInterface::class.java)
            try {
                val response = apiInterface?.getJsonObject(header, "${UrlUtils.getUrl()}/${table}/_security")
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
                    apiInterface.putDoc(header, "application/json", "${UrlUtils.getUrl()}/${table}/_security", jsonObject)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
