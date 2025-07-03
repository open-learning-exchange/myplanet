package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import android.util.Log
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

    fun uploadUserData(listener: SuccessListener) {
        val overallStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Starting")

        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        val transactionStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Starting async transaction")

        mRealm.executeTransactionAsync({ realm: Realm ->
            val queryStartTime = System.currentTimeMillis()
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Starting database query")

            val userModels: List<RealmUserModel> = realm.where(RealmUserModel::class.java)
                .isEmpty("_id").or().equalTo("isUpdated", true)
                .findAll()
                .take(100)

            val queryDuration = System.currentTimeMillis() - queryStartTime
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Database query took ${queryDuration}ms")
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Found ${userModels.size} users to process")

            if (userModels.isEmpty()) {
                Log.d("UploadTiming", "UploadToShelfService.uploadUserData: No users to process")
                return@executeTransactionAsync
            }

            val processingStartTime = System.currentTimeMillis()
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Starting user processing")

            val password = sharedPreferences.getString("loginUserPassword", "")
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Retrieved password from preferences")

            userModels.forEachIndexed { index, model ->
                val userStartTime = System.currentTimeMillis()
                Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Processing user ${index + 1}/${userModels.size} (${model.name})")

                try {
                    val headerStartTime = System.currentTimeMillis()
                    val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
                    val headerDuration = System.currentTimeMillis() - headerStartTime
                    Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Header creation took ${headerDuration}ms")

                    val checkUserStartTime = System.currentTimeMillis()
                    Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Checking if user exists for ${model.name}")
                    val userExists = checkIfUserExists(apiInterface, header, model)
                    val checkUserDuration = System.currentTimeMillis() - checkUserStartTime
                    Log.d("UploadTiming", "UploadToShelfService.uploadUserData: User existence check took ${checkUserDuration}ms - Exists: $userExists")

                    if (!userExists) {
                        val uploadNewStartTime = System.currentTimeMillis()
                        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Uploading new user ${model.name}")
                        uploadNewUser(apiInterface, realm, model)
                        val uploadNewDuration = System.currentTimeMillis() - uploadNewStartTime
                        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: New user upload took ${uploadNewDuration}ms")
                    } else if (model.isUpdated) {
                        val updateUserStartTime = System.currentTimeMillis()
                        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Updating existing user ${model.name}")
                        updateExistingUser(apiInterface, header, model)
                        val updateUserDuration = System.currentTimeMillis() - updateUserStartTime
                        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: User update took ${updateUserDuration}ms")
                    } else {
                        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: User ${model.name} already exists and not updated, skipping")
                    }

                    val userTotalDuration = System.currentTimeMillis() - userStartTime
                    Log.d("UploadTiming", "UploadToShelfService.uploadUserData: User ${index + 1} total processing time: ${userTotalDuration}ms")

                    // Log progress every 10 users
                    if ((index + 1) % 10 == 0) {
                        val progressDuration = System.currentTimeMillis() - processingStartTime
                        Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Progress ${index + 1}/${userModels.size} users, elapsed: ${progressDuration}ms")
                    }

                } catch (e: IOException) {
                    val userErrorDuration = System.currentTimeMillis() - userStartTime
                    Log.e("UploadTiming", "UploadToShelfService.uploadUserData: Error processing user ${model.name} after ${userErrorDuration}ms", e)
                    e.printStackTrace()
                }
            }

            val processingDuration = System.currentTimeMillis() - processingStartTime
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: All user processing completed in ${processingDuration}ms")

        }, {
            val transactionDuration = System.currentTimeMillis() - transactionStartTime
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Transaction completed in ${transactionDuration}ms")
            Log.d("UploadTiming", "UploadToShelfService.uploadUserData: Starting uploadToShelf")

            val uploadToShelfStartTime = System.currentTimeMillis()
            uploadToShelf(object : SuccessListener {
                override fun onSuccess(message: String?) {
                    val uploadToShelfDuration = System.currentTimeMillis() - uploadToShelfStartTime
                    val totalDuration = System.currentTimeMillis() - overallStartTime

                    Log.d("UploadTiming", "UploadToShelfService.uploadUserData: uploadToShelf completed in ${uploadToShelfDuration}ms")
                    Log.d("UploadTiming", "UploadToShelfService.uploadUserData: TOTAL METHOD DURATION: ${totalDuration}ms (${totalDuration/1000.0}s)")

                    listener.onSuccess(message)
                }
            })

        }) { error ->
            val errorDuration = System.currentTimeMillis() - overallStartTime
            Log.e("UploadTiming", "UploadToShelfService.uploadUserData: Error after ${errorDuration}ms", error)
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
                    val header = "Basic ${Base64.encodeToString(("${userModel.name}:${password}").toByteArray(), Base64.NO_WRAP)}"

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
        }, {
            uploadSingleUserToShelf(userName, listener)
        }) { error ->
            listener.onSuccess("Error during user data sync: ${error.localizedMessage}")
        }
    }

    private fun checkIfUserExists(apiInterface: ApiInterface?, header: String, model: RealmUserModel): Boolean {
        val startTime = System.currentTimeMillis()
        Log.d("UploadTiming", "checkIfUserExists: Starting for user ${model.name}")

        try {
            val networkStartTime = System.currentTimeMillis()
            val res = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")?.execute()
            val networkDuration = System.currentTimeMillis() - networkStartTime

            val exists = res?.body() != null
            val totalDuration = System.currentTimeMillis() - startTime

            Log.d("UploadTiming", "checkIfUserExists: Network call took ${networkDuration}ms, total: ${totalDuration}ms, exists: $exists")
            return exists

        } catch (e: IOException) {
            val errorDuration = System.currentTimeMillis() - startTime
            Log.e("UploadTiming", "checkIfUserExists: Error after ${errorDuration}ms for user ${model.name}", e)
            e.printStackTrace()
            return false
        }
    }

    private fun uploadNewUser(apiInterface: ApiInterface?, realm: Realm, model: RealmUserModel) {
        val startTime = System.currentTimeMillis()
        Log.d("UploadTiming", "uploadNewUser: Starting for user ${model.name}")

        try {
            val serializationStartTime = System.currentTimeMillis()
            val obj = model.serialize()
            val serializationDuration = System.currentTimeMillis() - serializationStartTime
            Log.d("UploadTiming", "uploadNewUser: Serialization took ${serializationDuration}ms")

            val createNetworkStartTime = System.currentTimeMillis()
            val createResponse = apiInterface?.putDoc(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)?.execute()
            val createNetworkDuration = System.currentTimeMillis() - createNetworkStartTime
            Log.d("UploadTiming", "uploadNewUser: Create user network call took ${createNetworkDuration}ms")

            if (createResponse?.isSuccessful == true) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString
                model._id = id
                model._rev = rev
                Log.d("UploadTiming", "uploadNewUser: User created successfully, starting post-creation processing")

                val postProcessStartTime = System.currentTimeMillis()
                processUserAfterCreation(apiInterface, realm, model, obj)
                val postProcessDuration = System.currentTimeMillis() - postProcessStartTime
                Log.d("UploadTiming", "uploadNewUser: Post-creation processing took ${postProcessDuration}ms")
            } else {
                Log.w("UploadTiming", "uploadNewUser: Create user failed for ${model.name}")
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("UploadTiming", "uploadNewUser: Total duration ${totalDuration}ms for user ${model.name}")

        } catch (e: IOException) {
            val errorDuration = System.currentTimeMillis() - startTime
            Log.e("UploadTiming", "uploadNewUser: Error after ${errorDuration}ms for user ${model.name}", e)
            e.printStackTrace()
        }
    }

    private fun processUserAfterCreation(apiInterface: ApiInterface?, realm: Realm, model: RealmUserModel, obj: JsonObject) {
        val startTime = System.currentTimeMillis()
        Log.d("UploadTiming", "processUserAfterCreation: Starting for user ${model.name}")

        try {
            val headerStartTime = System.currentTimeMillis()
            val password = sharedPreferences.getString("loginUserPassword", "")
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val headerDuration = System.currentTimeMillis() - headerStartTime
            Log.d("UploadTiming", "processUserAfterCreation: Header creation took ${headerDuration}ms")

            val fetchNetworkStartTime = System.currentTimeMillis()
            val fetchDataResponse = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/${model._id}")?.execute()
            val fetchNetworkDuration = System.currentTimeMillis() - fetchNetworkStartTime
            Log.d("UploadTiming", "processUserAfterCreation: Fetch user data took ${fetchNetworkDuration}ms")

            if (fetchDataResponse?.isSuccessful == true) {
                val dataProcessStartTime = System.currentTimeMillis()
                model.password_scheme = getString("password_scheme", fetchDataResponse.body())
                model.derived_key = getString("derived_key", fetchDataResponse.body())
                model.salt = getString("salt", fetchDataResponse.body())
                model.iterations = getString("iterations", fetchDataResponse.body())
                val dataProcessDuration = System.currentTimeMillis() - dataProcessStartTime
                Log.d("UploadTiming", "processUserAfterCreation: Data processing took ${dataProcessDuration}ms")

                val saveKeyStartTime = System.currentTimeMillis()
                Log.d("UploadTiming", "processUserAfterCreation: Starting saveKeyIv")
                if (saveKeyIv(apiInterface, model, obj)) {
                    val saveKeyDuration = System.currentTimeMillis() - saveKeyStartTime
                    Log.d("UploadTiming", "processUserAfterCreation: saveKeyIv took ${saveKeyDuration}ms")

                    val healthUpdateStartTime = System.currentTimeMillis()
                    updateHealthData(realm, model)
                    val healthUpdateDuration = System.currentTimeMillis() - healthUpdateStartTime
                    Log.d("UploadTiming", "processUserAfterCreation: Health update took ${healthUpdateDuration}ms")
                } else {
                    val saveKeyDuration = System.currentTimeMillis() - saveKeyStartTime
                    Log.w("UploadTiming", "processUserAfterCreation: saveKeyIv failed after ${saveKeyDuration}ms")
                }
            } else {
                Log.w("UploadTiming", "processUserAfterCreation: Fetch user data failed for ${model.name}")
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("UploadTiming", "processUserAfterCreation: Total duration ${totalDuration}ms for user ${model.name}")

        } catch (e: IOException) {
            val errorDuration = System.currentTimeMillis() - startTime
            Log.e("UploadTiming", "processUserAfterCreation: Error after ${errorDuration}ms for user ${model.name}", e)
            e.printStackTrace()
        }
    }

    private fun updateExistingUser(apiInterface: ApiInterface?, header: String, model: RealmUserModel) {
        val startTime = System.currentTimeMillis()
        Log.d("UploadTiming", "updateExistingUser: Starting for user ${model.name}")

        try {
            val fetchLatestStartTime = System.currentTimeMillis()
            val latestDocResponse = apiInterface?.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")?.execute()
            val fetchLatestDuration = System.currentTimeMillis() - fetchLatestStartTime
            Log.d("UploadTiming", "updateExistingUser: Fetch latest doc took ${fetchLatestDuration}ms")

            if (latestDocResponse?.isSuccessful == true) {
                val dataProcessStartTime = System.currentTimeMillis()
                val latestRev = latestDocResponse.body()?.get("_rev")?.asString
                val obj = model.serialize()
                val objMap = obj.entrySet().associate { (key, value) -> key to value }
                val mutableObj = mutableMapOf<String, Any>().apply { putAll(objMap) }
                latestRev?.let { rev -> mutableObj["_rev"] = rev as Any }

                val gson = Gson()
                val jsonElement = gson.toJsonTree(mutableObj)
                val jsonObject = jsonElement.asJsonObject
                val dataProcessDuration = System.currentTimeMillis() - dataProcessStartTime
                Log.d("UploadTiming", "updateExistingUser: Data processing took ${dataProcessDuration}ms")

                val updateNetworkStartTime = System.currentTimeMillis()
                val updateResponse = apiInterface.putDoc(header, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", jsonObject).execute()
                val updateNetworkDuration = System.currentTimeMillis() - updateNetworkStartTime
                Log.d("UploadTiming", "updateExistingUser: Update network call took ${updateNetworkDuration}ms")

                if (updateResponse.isSuccessful) {
                    val updatedRev = updateResponse.body()?.get("rev")?.asString
                    model._rev = updatedRev
                    model.isUpdated = false
                    Log.d("UploadTiming", "updateExistingUser: User updated successfully")
                } else {
                    Log.w("UploadTiming", "updateExistingUser: Update failed for user ${model.name}")
                }
            } else {
                Log.w("UploadTiming", "updateExistingUser: Failed to fetch latest doc for user ${model.name}")
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("UploadTiming", "updateExistingUser: Total duration ${totalDuration}ms for user ${model.name}")

        } catch (e: IOException) {
            val errorDuration = System.currentTimeMillis() - startTime
            Log.e("UploadTiming", "updateExistingUser: Error after ${errorDuration}ms for user ${model.name}", e)
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
        val startTime = System.currentTimeMillis()
        Log.d("UploadTiming", "saveKeyIv: Starting for user ${model.name}")

        val setupStartTime = System.currentTimeMillis()
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
        val setupDuration = System.currentTimeMillis() - setupStartTime
        Log.d("UploadTiming", "saveKeyIv: Setup took ${setupDuration}ms")

        var success = false
        var attemptCount = 0
        val maxAttempts = 3
        val retryDelayMs = 2000L
        val retryStartTime = System.currentTimeMillis()

        while (!success && attemptCount < maxAttempts) {
            attemptCount++
            val attemptStartTime = System.currentTimeMillis()
            Log.d("UploadTiming", "saveKeyIv: Attempt ${attemptCount}/${maxAttempts} for user ${model.name}")

            try {
                val networkCallStartTime = System.currentTimeMillis()
                val response: Response<JsonObject>? = apiInterface?.postDoc(header, "application/json", "${Utilities.getUrl()}/$table", ob)?.execute()
                val networkCallDuration = System.currentTimeMillis() - networkCallStartTime
                Log.d("UploadTiming", "saveKeyIv: Network call attempt $attemptCount took ${networkCallDuration}ms")

                if (response != null) {
                    if (response.isSuccessful && response.body() != null) {
                        model.key = keyString
                        model.iv = iv
                        success = true
                        val attemptDuration = System.currentTimeMillis() - attemptStartTime
                        Log.d("UploadTiming", "saveKeyIv: Attempt $attemptCount succeeded in ${attemptDuration}ms")
                    } else {
                        Log.w("UploadTiming", "saveKeyIv: Attempt $attemptCount failed - response not successful")
                        if (attemptCount < maxAttempts) {
                            Log.d("UploadTiming", "saveKeyIv: Sleeping ${retryDelayMs}ms before retry")
                            Thread.sleep(retryDelayMs)
                        }
                    }
                } else {
                    Log.w("UploadTiming", "saveKeyIv: Attempt $attemptCount failed - null response")
                    if (attemptCount < maxAttempts) {
                        Log.d("UploadTiming", "saveKeyIv: Sleeping ${retryDelayMs}ms before retry")
                        Thread.sleep(retryDelayMs)
                    }
                }
            } catch (e: Exception) {
                val attemptDuration = System.currentTimeMillis() - attemptStartTime
                Log.e("UploadTiming", "saveKeyIv: Attempt $attemptCount failed after ${attemptDuration}ms", e)

                if (attemptCount >= maxAttempts) {
                    val totalRetryDuration = System.currentTimeMillis() - retryStartTime
                    Log.e("UploadTiming", "saveKeyIv: All attempts failed after ${totalRetryDuration}ms")
                    throw IOException("Failed to save key/IV after $maxAttempts attempts", e)
                } else {
                    Log.d("UploadTiming", "saveKeyIv: Sleeping ${retryDelayMs}ms before retry")
                    Thread.sleep(retryDelayMs)
                }
            }
        }

        if (!success) {
            val totalRetryDuration = System.currentTimeMillis() - retryStartTime
            val errorMessage = "Failed to save key/IV after $maxAttempts attempts in ${totalRetryDuration}ms"
            Log.e("UploadTiming", "saveKeyIv: $errorMessage")
            throw IOException(errorMessage)
        }

        val securityChangeStartTime = System.currentTimeMillis()
        changeUserSecurity(model, obj)
        val securityChangeDuration = System.currentTimeMillis() - securityChangeStartTime
        Log.d("UploadTiming", "saveKeyIv: Security change took ${securityChangeDuration}ms")

        val totalDuration = System.currentTimeMillis() - startTime
        Log.d("UploadTiming", "saveKeyIv: Total duration ${totalDuration}ms for user ${model.name} (${attemptCount} attempts)")

        return true
    }

    fun uploadHealth() {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        mRealm.executeTransactionAsync { realm: Realm ->
            val myHealths: List<RealmMyHealthPojo> = realm.where(RealmMyHealthPojo::class.java).equalTo("isUpdated", true).notEqualTo("userId", "").findAll()
            myHealths.forEachIndexed { index, pojo ->
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

            val myHealths: List<RealmMyHealthPojo> = realm.where(RealmMyHealthPojo::class.java)
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

    private fun uploadToShelf(listener: SuccessListener) {
        val overallStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "uploadToShelf: Starting")

        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance

        val transactionStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "uploadToShelf: Starting async transaction")

        mRealm.executeTransactionAsync({ realm: Realm ->
            val queryStartTime = System.currentTimeMillis()
            Log.d("UploadTiming", "uploadToShelf: Starting user query")

            val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()

            val queryDuration = System.currentTimeMillis() - queryStartTime
            Log.d("UploadTiming", "uploadToShelf: User query took ${queryDuration}ms")
            Log.d("UploadTiming", "uploadToShelf: Found ${users.size} users to process")

            if (users.isEmpty()) {
                Log.d("UploadTiming", "uploadToShelf: No users to process")
                return@executeTransactionAsync
            }

            val processingStartTime = System.currentTimeMillis()
            Log.d("UploadTiming", "uploadToShelf: Starting user processing")

            var processedCount = 0
            var skippedCount = 0
            var errorCount = 0

            users.forEachIndexed { index, model ->
                val userStartTime = System.currentTimeMillis()
                Log.d("UploadTiming", "uploadToShelf: Processing user ${index + 1}/${users.size} (${model.id})")

                try {
                    if (model.id?.startsWith("guest") == true) {
                        skippedCount++
                        Log.d("UploadTiming", "uploadToShelf: Skipping guest user ${model.id}")
                        return@forEachIndexed
                    }

                    // First network call - get shelf by _id
                    val firstCallStartTime = System.currentTimeMillis()
                    Log.d("UploadTiming", "uploadToShelf: Making first network call for user ${model.id}")
                    val jsonDoc = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${model._id}")?.execute()?.body()
                    val firstCallDuration = System.currentTimeMillis() - firstCallStartTime
                    Log.d("UploadTiming", "uploadToShelf: First network call took ${firstCallDuration}ms")

                    // Heavy data processing
                    val dataProcessStartTime = System.currentTimeMillis()
                    Log.d("UploadTiming", "uploadToShelf: Starting getShelfData processing for user ${model.id}")
                    val `object` = getShelfData(realm, model.id, jsonDoc)
                    val dataProcessDuration = System.currentTimeMillis() - dataProcessStartTime
                    Log.d("UploadTiming", "uploadToShelf: getShelfData processing took ${dataProcessDuration}ms")

                    // Second network call - get shelf by id
                    val secondCallStartTime = System.currentTimeMillis()
                    Log.d("UploadTiming", "uploadToShelf: Making second network call for user ${model.id}")
                    val d = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${model.id}")?.execute()?.body()
                    val secondCallDuration = System.currentTimeMillis() - secondCallStartTime
                    Log.d("UploadTiming", "uploadToShelf: Second network call took ${secondCallDuration}ms")

                    // Update object with revision
                    val revUpdateStartTime = System.currentTimeMillis()
                    `object`.addProperty("_rev", getString("_rev", d))
                    val revUpdateDuration = System.currentTimeMillis() - revUpdateStartTime
                    Log.d("UploadTiming", "uploadToShelf: Rev update took ${revUpdateDuration}ms")

                    // Third network call - put updated data
                    val thirdCallStartTime = System.currentTimeMillis()
                    Log.d("UploadTiming", "uploadToShelf: Making third network call for user ${model.id}")
                    val putResponse = apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}", `object`)?.execute()?.body()
                    val thirdCallDuration = System.currentTimeMillis() - thirdCallStartTime
                    Log.d("UploadTiming", "uploadToShelf: Third network call took ${thirdCallDuration}ms")

                    processedCount++

                    val userTotalDuration = System.currentTimeMillis() - userStartTime
                    Log.d("UploadTiming", "uploadToShelf: User ${index + 1} total processing time: ${userTotalDuration}ms")
                    Log.d("UploadTiming", "uploadToShelf: User ${index + 1} breakdown - First call: ${firstCallDuration}ms, Data processing: ${dataProcessDuration}ms, Second call: ${secondCallDuration}ms, Third call: ${thirdCallDuration}ms")

                    // Log progress every 10 users
                    if ((index + 1) % 10 == 0) {
                        val progressDuration = System.currentTimeMillis() - processingStartTime
                        val avgTimePerUser = progressDuration / (index + 1)
                        val estimatedTotal = avgTimePerUser * users.size
                        val remainingTime = estimatedTotal - progressDuration
                        Log.d("UploadTiming", "uploadToShelf: Progress ${index + 1}/${users.size} users")
                        Log.d("UploadTiming", "uploadToShelf: Elapsed: ${progressDuration}ms, Avg per user: ${avgTimePerUser}ms")
                        Log.d("UploadTiming", "uploadToShelf: Estimated remaining time: ${remainingTime}ms")
                    }

                } catch (e: Exception) {
                    errorCount++
                    val userErrorDuration = System.currentTimeMillis() - userStartTime
                    Log.e("UploadTiming", "uploadToShelf: Error processing user ${model.id} after ${userErrorDuration}ms", e)
                    e.printStackTrace()
                }
            }

            val processingDuration = System.currentTimeMillis() - processingStartTime
            Log.d("UploadTiming", "uploadToShelf: All user processing completed in ${processingDuration}ms")
            Log.d("UploadTiming", "uploadToShelf: Processed: $processedCount, Skipped: $skippedCount, Errors: $errorCount")

            if (processedCount > 0) {
                val avgTimePerUser = processingDuration / processedCount
                Log.d("UploadTiming", "uploadToShelf: Average time per processed user: ${avgTimePerUser}ms")
            }

        }, {
            val transactionDuration = System.currentTimeMillis() - transactionStartTime
            val totalDuration = System.currentTimeMillis() - overallStartTime

            Log.d("UploadTiming", "uploadToShelf: Transaction completed in ${transactionDuration}ms")
            Log.d("UploadTiming", "uploadToShelf: TOTAL METHOD DURATION: ${totalDuration}ms (${totalDuration/1000.0}s)")

            listener.onSuccess("Sync with server completed successfully")

        }) { error ->
            val errorDuration = System.currentTimeMillis() - overallStartTime
            Log.e("UploadTiming", "uploadToShelf: Error after ${errorDuration}ms", error)
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

                    val shelfUrl = "${Utilities.getUrl()}/shelf/${model._id}"
                    val jsonDoc = apiInterface?.getJsonObject(Utilities.header, shelfUrl)?.execute()?.body()
                    val shelfObject = getShelfData(realm, model.id, jsonDoc)

                    val revDoc = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${model.id}")?.execute()?.body()
                    shelfObject.addProperty("_rev", getString("_rev", revDoc))

                    val targetUrl = "${Utilities.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}"
                    apiInterface?.putDoc(Utilities.header, "application/json", targetUrl, shelfObject)?.execute()?.body()
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
