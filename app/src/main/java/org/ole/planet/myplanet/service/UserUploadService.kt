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
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmMyHealthPojo.Companion.serialize
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response
import java.io.IOException
import java.util.Date

class UserUploadService(context: Context) {
    private val dbService: DatabaseService = DatabaseService(context)
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm

    fun uploadUserData(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync({ realm: Realm ->
            val userModels = realm.where(RealmUserModel::class.java)
                .isEmpty("_id").or().equalTo("isUpdated", true)
                .findAll()
                .take(100)
            userModels.forEach { model ->
                try {
                    val password = sharedPreferences.getString("loginUserPassword", "")
                    val header = "Basic ${Base64.encodeToString(("${model.name}:$password").toByteArray(), Base64.NO_WRAP)}"
                    val userExists = checkIfUserExists(apiInterface, header, model)
                    if (!userExists) {
                        uploadNewUser(apiInterface, realm, model)
                    } else if (model.isUpdated) {
                        updateExistingUser(apiInterface, header, model)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, { listener.onSuccess("User data upload complete") }) { error ->
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
                    val header = "Basic ${Base64.encodeToString(("${userModel.name}:$password").toByteArray(), Base64.NO_WRAP)}"
                    val userExists = checkIfUserExists(apiInterface, header, userModel)
                    if (!userExists) {
                        uploadNewUser(apiInterface, realm, userModel)
                    } else if (userModel.isUpdated) {
                        updateExistingUser(apiInterface, header, userModel)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, { listener.onSuccess("User data upload complete") }) { error ->
            listener.onSuccess("Error during user data sync: ${error.localizedMessage}")
        }
    }

    private fun checkIfUserExists(apiInterface: ApiInterface?, header: String, model: RealmUserModel): Boolean {
        return try {
            val res = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")?.execute()
            res?.body() != null
        } catch (e: IOException) {
            e.printStackTrace()
            false
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
            val header = "Basic ${Base64.encodeToString(("${model.name}:$password").toByteArray(), Base64.NO_WRAP)}"
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
        val replacedUrl = url.replaceFirst("[^:]+:[^@]+@".toRegex(), "${model.name}:$password@")
        val protocolIndex = url.indexOf("://")
        val protocol = url.substring(0, protocolIndex)
        return "$protocol://$replacedUrl"
    }

    private fun updateHealthData(realm: Realm, model: RealmUserModel) {
        val list = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", model.id).findAll()
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
        var success = false
        var attemptCount = 0
        val maxAttempts = 3
        val retryDelayMs = 2000L
        while (!success && attemptCount < maxAttempts) {
            attemptCount++
            try {
                val response = apiInterface?.postDoc(header, "application/json", "${Utilities.getUrl()}/$table", ob)?.execute()
                if (response != null) {
                    if (response.isSuccessful && response.body() != null) {
                        model.key = keyString
                        model.iv = iv
                        success = true
                    } else if (attemptCount < maxAttempts) {
                        Thread.sleep(retryDelayMs)
                    }
                } else if (attemptCount < maxAttempts) {
                    Thread.sleep(retryDelayMs)
                }
            } catch (e: Exception) {
                if (attemptCount >= maxAttempts) {
                    throw IOException("Failed to save key/IV after $maxAttempts attempts", e)
                } else {
                    Thread.sleep(retryDelayMs)
                }
            }
        }
        if (!success) {
            val errorMessage = "Failed to save key/IV after $maxAttempts attempts"
            throw IOException(errorMessage)
        }
        changeUserSecurity(model, obj)
        return true
    }

    fun uploadHealth() {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync { realm: Realm ->
            val myHealths = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("isUpdated", true)
                .notEqualTo("userId", "")
                .findAll()
            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/health", serialize(pojo))?.execute()
                    if (res?.body() != null && res.body()?.has("id") == true) {
                        pojo._rev = res.body()!!["rev"].asString
                        pojo.isUpdated = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun uploadSingleUserHealth(userId: String?, listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync({ realm: Realm ->
            if (userId.isNullOrEmpty()) {
                return@executeTransactionAsync
            }
            val myHealths = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("isUpdated", true)
                .equalTo("userId", userId)
                .findAll()
            myHealths.forEach { pojo ->
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
        }, {
            listener?.onSuccess("Health data for user $userId uploaded successfully")
        }) { error ->
            listener?.onSuccess("Error uploading health data for user $userId: ${error.localizedMessage}")
        }
    }

    companion object {
        var instance: UserUploadService? = null
            get() {
                if (field == null) {
                    field = UserUploadService(MainApplication.context)
                }
                return field
            }
            private set

        private fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
            val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
            val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
            val apiInterface = client?.create(ApiInterface::class.java)
            try {
                val response = apiInterface?.getJsonObject(header, "${Utilities.getUrl()}/$table/_security")?.execute()
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

