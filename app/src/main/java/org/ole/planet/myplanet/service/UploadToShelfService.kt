package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getMyMeetUpIds
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseIds
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmMyHealthPojo.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibIds
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.removedIds
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response
import java.io.IOException
import java.util.Date

class UploadToShelfService(context: Context) {
    private val dbService: DatabaseService = DatabaseService(context)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm

    private fun authHeader(user: String?, password: String?): String {
        val creds = "${user ?: ""}:${password ?: ""}"
        return "Basic ${Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)}"
    }

    private fun processUser(apiInterface: ApiInterface?, realm: Realm, model: RealmUserModel, password: String?) {
        val header = authHeader(model.name, password)
        val userExists = checkIfUserExists(apiInterface, header, model)

        if (!userExists) {
            uploadNewUser(apiInterface, realm, model)
        } else if (model.isUpdated) {
            updateExistingUser(apiInterface, header, model)
        }
    }

    private fun uploadHealthEntries(apiInterface: ApiInterface?, healths: List<RealmMyHealthPojo>) {
        healths.forEach { pojo ->
            try {
                val res = apiInterface?.postDoc(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/health",
                    serialize(pojo)
                )?.execute()

                if (res?.body() != null && res.body()?.has("id") == true) {
                    pojo._rev = res.body()!!["rev"].asString
                    pojo.isUpdated = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun syncShelf(apiInterface: ApiInterface?, realm: Realm, model: RealmUserModel) {
        val shelfUrl = "${Utilities.getUrl()}/shelf/${model._id}"
        val jsonDoc = apiInterface?.getJsonObject(Utilities.header, shelfUrl)?.execute()?.body()
        val shelfObject = getShelfData(realm, model.id, jsonDoc)

        val revDoc = apiInterface?.getJsonObject(
            Utilities.header,
            "${Utilities.getUrl()}/shelf/${model.id}"
        )?.execute()?.body()
        shelfObject.addProperty("_rev", getString("_rev", revDoc))

        val targetUrl = "${Utilities.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}"
        apiInterface?.putDoc(Utilities.header, "application/json", targetUrl, shelfObject)?.execute()?.body()
    }

    @Throws(IOException::class)
    private fun postWithRetry(
        apiInterface: ApiInterface?,
        header: String,
        url: String,
        body: JsonObject,
        maxAttempts: Int = 3,
        retryDelayMs: Long = 2000L
    ): Response<JsonObject>? {
        var attempt = 0
        var lastException: Exception? = null
        while (attempt < maxAttempts) {
            try {
                return apiInterface?.postDoc(header, "application/json", url, body)?.execute()
            } catch (e: Exception) {
                lastException = e
                if (++attempt < maxAttempts) {
                    Thread.sleep(retryDelayMs)
                }
            }
        }
        throw IOException("Failed to post after $maxAttempts attempts", lastException)
    }

    fun uploadUserData(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync({ realm: Realm ->
            val password = sharedPreferences.getString("loginUserPassword", "")
            val userModels: List<RealmUserModel> = realm.where(RealmUserModel::class.java)
                .isEmpty("_id").or().equalTo("isUpdated", true)
                .findAll()
                .take(100)
            userModels.forEach { model ->
                try {
                    processUser(apiInterface, realm, model, password)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, {
            uploadToShelf(listener)
        }) { error ->
            listener.onSuccess("Error during user data sync: ${error.localizedMessage}")
        }
    }

    fun uploadSingleUserData(userName: String?, listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        mRealm.executeTransactionAsync({ realm: Realm ->
            val userModel = realm.where(RealmUserModel::class.java)
                .equalTo("name", userName)
                .findFirst()

            if (userModel != null) {
                try {
                    val password = sharedPreferences.getString("loginUserPassword", "")
                    processUser(apiInterface, realm, userModel, password)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, {
            uploadSingleUserToShelf(userName, listener)
        }) { error ->
            listener.onSuccess("Error during user data sync: ${error.localizedMessage}")
        }
    }

    private fun checkIfUserExists(apiInterface: ApiInterface?, header: String, model: RealmUserModel): Boolean {
        try {
            val res = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")?.execute()
            return res?.body() != null
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    private fun uploadNewUser(apiInterface: ApiInterface?, realm: Realm, model: RealmUserModel) {
        try {
            val obj = model.serialize()
            val createResponse = apiInterface?.putDoc(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)?.execute()

            if (createResponse?.isSuccessful == true) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString
                model._id = id
                model._rev = rev

                processUserAfterCreation(apiInterface, realm, model, obj)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processUserAfterCreation(apiInterface: ApiInterface?, realm: Realm, model: RealmUserModel, obj: JsonObject) {
        try {
            val password = sharedPreferences.getString("loginUserPassword", "")
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"

            val fetchDataResponse = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/${model._id}")?.execute()
            if (fetchDataResponse?.isSuccessful == true) {
                model.password_scheme = getString("password_scheme", fetchDataResponse.body())
                model.derived_key = getString("derived_key", fetchDataResponse.body())
                model.salt = getString("salt", fetchDataResponse.body())
                model.iterations = getString("iterations", fetchDataResponse.body())

                if (saveKeyIv(apiInterface, model, obj)) {
                    updateHealthData(realm, model)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun updateExistingUser(apiInterface: ApiInterface?, header: String, model: RealmUserModel) {
        try {
            val latestDocResponse = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")?.execute()

            if (latestDocResponse?.isSuccessful == true) {
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
                    val updatedRev = updateResponse.body()?.get("rev")?.asString
                    model._rev = updatedRev
                    model.isUpdated = false
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun replacedUrl(model: RealmUserModel): String {
        val url = Utilities.getUrl()
        val password = sharedPreferences.getString("loginUserPassword", "")
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

    @Throws(IOException::class)
    fun saveKeyIv(apiInterface: ApiInterface?, model: RealmUserModel, obj: JsonObject): Boolean {
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
        val response = postWithRetry(
            apiInterface,
            header,
            "${Utilities.getUrl()}/$table",
            ob
        )

        if (response != null && response.isSuccessful && response.body() != null) {
            model.key = keyString
            model.iv = iv
        } else {
            throw IOException("Failed to save key/IV")
        }

        changeUserSecurity(model, obj)
        return true
    }

    fun uploadHealth() {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        mRealm.executeTransactionAsync { realm: Realm ->
            val myHealths: List<RealmMyHealthPojo> = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("isUpdated", true)
                .notEqualTo("userId", "")
                .findAll()
            uploadHealthEntries(apiInterface, myHealths)
        }
    }

    fun uploadSingleUserHealth(userId: String?, listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        mRealm.executeTransactionAsync({ realm: Realm ->
            if (userId.isNullOrEmpty()) {
                return@executeTransactionAsync
            }

            val myHealths: List<RealmMyHealthPojo> = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("isUpdated", true)
                .equalTo("userId", userId)
                .findAll()
            uploadHealthEntries(apiInterface, myHealths)
        }, {
            listener?.onSuccess("Health data for user $userId uploaded successfully")
        }) { error ->
            listener?.onSuccess("Error uploading health data for user $userId: ${error.localizedMessage}")
        }
    }

    private fun uploadToShelf(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        mRealm.executeTransactionAsync({ realm: Realm ->
            val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()
            users.forEach { model ->
                try {
                    if (model.id?.startsWith("guest") == true) {
                        return@forEach
                    }
                    syncShelf(apiInterface, realm, model)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
            { listener.onSuccess("Sync with server completed successfully") })
        { error ->
            listener.onSuccess("Unable to update documents: ${error.localizedMessage}")
        }
    }

    private fun uploadSingleUserToShelf(userName: String?, listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        mRealm.executeTransactionAsync({ realm: Realm ->
            val model = realm.where(RealmUserModel::class.java)
                .equalTo("name", userName)
                .isNotEmpty("_id")
                .findFirst()

            if (model != null) {
                try {
                    if (model.id?.startsWith("guest") == true) return@executeTransactionAsync
                    syncShelf(apiInterface, realm, model)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, {
            listener.onSuccess("Single user shelf sync completed successfully")
        }) { error ->
            listener.onSuccess("Unable to update document: ${error.localizedMessage}")
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
            val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
            val apiInterface = client?.create(ApiInterface::class.java)
            try {
                val response: Response<JsonObject?>? = apiInterface?.getJsonObject(header, "${Utilities.getUrl()}/${table}/_security")?.execute()
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
                    apiInterface.putDoc(header, "application/json", "${Utilities.getUrl()}/${table}/_security", jsonObject).execute()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
