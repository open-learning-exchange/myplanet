package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmHealthExamination.Companion.serialize
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.RetryUtils
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@Singleton
class UploadToShelfService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences,
    private val sharedPrefManager: SharedPrefManager,
    private val resourcesRepository: ResourcesRepository,
    private val coursesRepository: CoursesRepository,
    private val userRepository: UserRepository,
    private val healthRepository: HealthRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val apiInterface: ApiInterface
) {

    fun uploadUserData(listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            try {
                val userModels = userRepository.getPendingSyncUsers(100)

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
                        listener.onSuccess(success)
                    }
                })
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    fun uploadSingleUserData(userName: String?, listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            try {
                val userModel = if (userName != null) userRepository.getUserByName(userName) else null

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
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun checkIfUserExists(apiInterface: ApiInterface, header: String, model: RealmUser): Boolean {
        try {
            val res = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")
            val exists = res.body() != null
            return exists
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun uploadNewUser(apiInterface: ApiInterface, model: RealmUser) {
        try {
            val obj = model.serialize()
            val createResponse = apiInterface.putDoc(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)

            if (createResponse.isSuccessful) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString
                model._id = id
                model._rev = rev

                // Persist _id and _rev to database
                userRepository.markUserUploaded(model.id ?: "", id ?: "", rev ?: "")

                processUserAfterCreation(apiInterface, model, obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processUserAfterCreation(apiInterface: ApiInterface, model: RealmUser, obj: JsonObject) {
        try {
            val password = model.password ?: SecurePrefs.getPassword(context, sharedPreferences) ?: ""
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val fetchDataResponse = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/${model._id}")

            if (fetchDataResponse.isSuccessful) {
                val passwordScheme = getString("password_scheme", fetchDataResponse.body())
                val derivedKey = getString("derived_key", fetchDataResponse.body())
                val salt = getString("salt", fetchDataResponse.body())
                val iterations = getString("iterations", fetchDataResponse.body())

                model.password_scheme = passwordScheme
                model.derived_key = derivedKey
                model.salt = salt
                model.iterations = iterations

                userRepository.updateSecurityData(
                    model.name ?: "",
                    model._id,
                    model._rev,
                    derivedKey,
                    salt,
                    passwordScheme,
                    iterations
                )

                saveKeyIv(apiInterface, model, obj)

                healthRepository.updateExaminationUserId(model.id ?: "", model._id ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun updateExistingUser(apiInterface: ApiInterface, header: String, model: RealmUser) {
        try {
            val latestDocResponse = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")

            if (latestDocResponse.isSuccessful) {
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
                    userRepository.markUserRevUpdated(model.id ?: "", updatedRev)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun replacedUrl(model: RealmUser): String {
        val url = UrlUtils.getUrl()
        val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
        val replacedUrl = url.replaceFirst("[^:]+:[^@]+@".toRegex(), "${model.name}:${password}@")
        val protocolIndex = url.indexOf("://")
        val protocol = url.substring(0, protocolIndex)
        return "$protocol://$replacedUrl"
    }

    suspend fun saveKeyIv(apiInterface: ApiInterface, model: RealmUser, obj: JsonObject) {
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

        withContext(dispatcherProvider.io) {
            try {
                apiInterface.putDoc(header, "application/json", dbUrl, JsonObject())
            } catch (e: Exception) {
                null
            }
        }

        val response = withContext(dispatcherProvider.io) {
            RetryUtils.retry(
                maxAttempts = maxAttempts,
                delayMs = retryDelayMs,
                shouldRetry = { resp -> resp == null || !resp.isSuccessful || resp.body() == null }
            ) {
                apiInterface.postDoc(header, "application/json", "${UrlUtils.getUrl()}/$table", ob)
            }
        }

        if (response?.isSuccessful == true && response.body() != null) {
            changeUserSecurity(model, obj)

            userRepository.markUserKeyIvSaved(model.id ?: "", keyString ?: "", iv)
        } else {
            throw IOException("Failed to save key/IV after $maxAttempts attempts")
        }
    }

    fun uploadHealth() {
        appScope.launch(dispatcherProvider.io) {
            val myHealths = healthRepository.getUpdatedHealthExaminations()

            val uploadedHealths = mutableMapOf<String, String?>()
            myHealths.forEach { pojo ->
                try {
                    val res = apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/health", serialize(pojo))

                    if (res.body() != null && res.body()?.has("id") == true) {
                        val rev = res.body()?.get("rev")?.asString
                        pojo._id?.let { id ->
                            uploadedHealths[id] = rev
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            healthRepository.markHealthExaminationsUploaded(uploadedHealths)
        }
    }

    fun uploadSingleUserHealth(userId: String?, listener: OnSuccessListener?) {
        appScope.launch(dispatcherProvider.io) {
            try {
                if (userId.isNullOrEmpty()) return@launch

                val myHealths = healthRepository.getUpdatedHealthForUser(userId)

                val uploadedHealths = mutableMapOf<String, String?>()
                myHealths.forEach { pojo ->
                    try {
                        val res = apiInterface.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/health",
                            serialize(pojo)
                        )

                        if (res.body() != null && res.body()?.has("id") == true) {
                            val rev = res.body()?.get("rev")?.asString
                            pojo._id?.let { id ->
                                uploadedHealths[id] = rev
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                healthRepository.markHealthExaminationsUploaded(uploadedHealths)

                withContext(dispatcherProvider.main) {
                    listener?.onSuccess("Health data for user $userId uploaded successfully")
                }
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
                    listener?.onSuccess("Error uploading health data for user $userId: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadToShelf(listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            val unmanagedUsers = userRepository.getSyncedUsers()

            if (unmanagedUsers.isEmpty()) {
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
                return@launch
            }

            try {
                unmanagedUsers.forEach { model ->
                    try {
                        val jsonDoc = apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/${model._id}").body()
                        val myLibs = resourcesRepository.getMyLibIds(model.id ?: "")
                        val myCourseIds = coursesRepository.getMyCourseIds(model.id ?: "")
                        val shelfData = userRepository.getShelfData(model.id, jsonDoc, myLibs, myCourseIds)
                        shelfData.addProperty("_rev", getString("_rev", jsonDoc))
                        apiInterface.putDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/shelf/${sharedPrefManager.getUserId()}",
                            shelfData
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Unable to update documents: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadSingleUserToShelf(userName: String?, listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            try {
                val model = userName?.let { userRepository.getSyncedUserByName(it) }

                if (model != null) {
                    val shelfUrl = "${UrlUtils.getUrl()}/shelf/${model._id}"
                    val jsonDoc = apiInterface.getJsonObject(UrlUtils.header, shelfUrl).body()
                    val myLibs = resourcesRepository.getMyLibIds(model.id ?: "")
                    val myCourseIds = coursesRepository.getMyCourseIds(model.id ?: "")
                    val shelfObject = userRepository.getShelfData(model.id, jsonDoc, myLibs, myCourseIds)
                    shelfObject.addProperty("_rev", getString("_rev", jsonDoc))

                    val targetUrl = "${UrlUtils.getUrl()}/shelf/${sharedPrefManager.getUserId()}"
                    apiInterface.putDoc(UrlUtils.header, "application/json", targetUrl, shelfObject)
                }
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Single user shelf sync completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Unable to update document: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun changeUserSecurity(model: RealmUser, obj: JsonObject) {
        val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
        val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
        try {
            val response = apiInterface.getJsonObject(header, "${UrlUtils.getUrl()}/${table}/_security")
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
                apiInterface.putDoc(header, "application/json", "${UrlUtils.getUrl()}/${table}/_security", jsonObject)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
