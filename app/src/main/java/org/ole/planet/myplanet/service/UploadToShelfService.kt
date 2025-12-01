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
import kotlinx.coroutines.Dispatchers
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
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadToShelfService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences
) {
    suspend fun uploadUserData() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

            val userModels = dbService.withRealm { realm ->
                val users = realm.where(RealmUserModel::class.java)
                    .isEmpty("_id").or().equalTo("isUpdated", true)
                    .findAll()
                    .take(100)
                realm.copyFromRealm(users)
            }

            if (userModels.isEmpty()) {
                uploadToShelf()
                return@withContext
            }

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

            uploadToShelf()
        }
    }

    suspend fun uploadSingleUserData(userName: String?) {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

            val userModel = dbService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("name", userName)
                    .findFirst()
                    ?.let { realm.copyFromRealm(it) }
            } ?: return@withContext

            try {
                val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                val header = "Basic ${Base64.encodeToString(("${userModel.name}:${password}").toByteArray(), Base64.NO_WRAP)}"

                val userExists = checkIfUserExists(apiInterface, header, userModel)

                if (!userExists) {
                    uploadNewUser(apiInterface, userModel)
                } else if (userModel.isUpdated) {
                    updateExistingUser(apiInterface, header, userModel)
                }

                uploadSingleUserToShelf(userName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun checkIfUserExists(apiInterface: ApiInterface, header: String, model: RealmUserModel): Boolean {
        return try {
            val res = apiInterface.getJsonObjectSuspended(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")
            res.body() != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun uploadNewUser(apiInterface: ApiInterface, model: RealmUserModel) {
        try {
            val obj = model.serialize()
            val createResponse = apiInterface.putDocSuspend(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)

            if (createResponse.isSuccessful) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString

                dbService.withRealm { realm ->
                    realm.executeTransaction { r ->
                        val dbModel = r.where(RealmUserModel::class.java).equalTo("id", model.id).findFirst()
                        dbModel?._id = id
                        dbModel?._rev = rev
                    }
                }

                model._id = id
                model._rev = rev
                processUserAfterCreation(apiInterface, model, obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processUserAfterCreation(apiInterface: ApiInterface, model: RealmUserModel, obj: JsonObject) {
        try {
            val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val fetchDataResponse = apiInterface.getJsonObjectSuspended(header, "${replacedUrl(model)}/_users/${model._id}")

            if (fetchDataResponse.isSuccessful) {
                model.password_scheme = getString("password_scheme", fetchDataResponse.body())
                model.derived_key = getString("derived_key", fetchDataResponse.body())
                model.salt = getString("salt", fetchDataResponse.body())
                model.iterations = getString("iterations", fetchDataResponse.body())

                if (saveKeyIv(apiInterface, model, obj)) {
                    updateHealthData(model)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun updateExistingUser(apiInterface: ApiInterface, header: String, model: RealmUserModel) {
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
                    dbService.withRealm { realm ->
                        realm.executeTransaction { r ->
                            val dbModel = r.where(RealmUserModel::class.java).equalTo("id", model.id).findFirst()
                            dbModel?._rev = updatedRev
                            dbModel?.isUpdated = false
                        }
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

    private suspend fun updateHealthData(model: RealmUserModel) {
        dbService.withRealm { realm ->
            realm.executeTransaction { r ->
                val list = r.where(RealmMyHealthPojo::class.java).equalTo("_id", model.id).findAll()
                for (p in list) {
                    p.userId = model._id
                }
            }
        }
    }

    @Throws(IOException::class)
    suspend fun saveKeyIv(apiInterface: ApiInterface?, model: RealmUserModel, obj: JsonObject): Boolean {
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
                apiInterface?.postDocSuspend(header, "application/json", "${UrlUtils.getUrl()}/$table", ob)
            }

        if (response?.isSuccessful == true && response.body() != null) {
            dbService.withRealm { realm ->
                realm.executeTransaction { r ->
                    // Assuming model is managed or we find it. Model passed here might be unmanaged.
                    // If called from Service.saveUserToDb, model comes from saveUser(it, settings), which might return managed object?
                    // Service.saveUserToDb returns RealmUserModel? which is result of userRepository.saveUser.
                    // UserRepository usually returns managed object.
                    // But if we are in suspend function context, we should use ID to find it.
                    val dbModel = r.where(RealmUserModel::class.java).equalTo("id", model.id).findFirst()
                    dbModel?.key = keyString
                    dbModel?.iv = iv
                }
            }
            // Update the passed model reference too if it's unmanaged and used later
            model.key = keyString
            model.iv = iv
        } else {
            throw IOException("Failed to save key/IV after $maxAttempts attempts")
        }

        changeUserSecurity(model, obj)
        return true
    }

    suspend fun uploadHealth() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

            val myHealths = dbService.withRealm { realm ->
                val healths = realm.where(RealmMyHealthPojo::class.java)
                    .equalTo("isUpdated", true).notEqualTo("userId", "")
                    .findAll()
                realm.copyFromRealm(healths)
            }

            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/health", serialize(pojo))

                    if (res.isSuccessful && res.body()?.has("id") == true) {
                        val rev = res.body()?.get("rev")?.asString
                        dbService.withRealm { realm ->
                            realm.executeTransaction { r ->
                                val dbPojo = r.where(RealmMyHealthPojo::class.java).equalTo("_id", pojo._id).findFirst()
                                dbPojo?._rev = rev
                                dbPojo?.isUpdated = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadSingleUserHealth(userId: String?) {
        withContext(Dispatchers.IO) {
             if (userId.isNullOrEmpty()) return@withContext
             val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

             val myHealths = dbService.withRealm { realm ->
                 val healths = realm.where(RealmMyHealthPojo::class.java)
                    .equalTo("isUpdated", true)
                    .equalTo("userId", userId)
                    .findAll()
                 realm.copyFromRealm(healths)
             }

             myHealths.forEach { pojo ->
                try {
                    val res = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/health",
                        serialize(pojo)
                    )

                    if (res.isSuccessful && res.body()?.has("id") == true) {
                        val rev = res.body()?.get("rev")?.asString
                        dbService.withRealm { realm ->
                            realm.executeTransaction { r ->
                                val dbPojo = r.where(RealmMyHealthPojo::class.java).equalTo("_id", pojo._id).findFirst()
                                dbPojo?._rev = rev
                                dbPojo?.isUpdated = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun uploadToShelf() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val unmanagedUsers = dbService.withRealm { realm ->
                val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()
                realm.copyFromRealm(users)
            }

            if (unmanagedUsers.isEmpty()) return@withContext

            unmanagedUsers.forEach { model ->
                try {
                    if (model.id?.startsWith("guest") == true) return@forEach
                    val jsonDoc = apiInterface.getJsonObjectSuspended(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/${model._id}").body()

                    val shelfData = dbService.withRealm { realm ->
                         getShelfData(realm, model.id, jsonDoc)
                    }

                    shelfData.addProperty("_rev", getString("_rev", jsonDoc))
                    apiInterface.putDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}", shelfData)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun uploadSingleUserToShelf(userName: String?) {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

            val model = dbService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("name", userName)
                    .isNotEmpty("_id")
                    .findFirst()
                    ?.let { realm.copyFromRealm(it) }
            }

            if (model != null) {
                try {
                    if (model.id?.startsWith("guest") == true) return@withContext

                    val shelfUrl = "${UrlUtils.getUrl()}/shelf/${model._id}"
                    val jsonDoc = apiInterface.getJsonObjectSuspended(UrlUtils.header, shelfUrl).body()

                    val shelfObject = dbService.withRealm { realm ->
                        getShelfData(realm, model.id, jsonDoc)
                    }

                    shelfObject.addProperty("_rev", getString("_rev", jsonDoc))

                    val targetUrl = "${UrlUtils.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}" 
                    apiInterface.putDocSuspend(UrlUtils.header, "application/json", targetUrl, shelfObject)
                } catch (e: Exception) {
                    e.printStackTrace()
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
            // This method was synchronous. It should be refactored or wrapped.
            // But since it's companion, I can't easily make it suspend unless I change all calls.
            // It uses execute() on Call.
            // I should make it suspend.

            // Wait, this is called from saveKeyIv (suspend).
            // So I can launch it in scope? No, saveKeyIv is suspended, so it can call suspend function.
            // But this is companion object method.

            // I will implement it inside the class or make it a suspend function in companion if possible?
            // Or just inline it? It is private.
        }
    }

    // Moved changeUserSecurity inside class or make it suspend in companion (requires context/client access which it has via ApiClient.client)
    private suspend fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
         val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
         val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
         val apiInterface = client?.create(ApiInterface::class.java) ?: return
         try {
             val response = apiInterface.getJsonObjectSuspended(header, "${UrlUtils.getUrl()}/${table}/_security")
             if (response.isSuccessful && response.body() != null) {
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
         } catch (e: Exception) {
             e.printStackTrace()
         }
    }
}
