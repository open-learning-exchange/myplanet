package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.myhealth.HealthRecord
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.UrlUtils

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    private val apiInterface: ApiInterface,
    private val uploadToShelfService: UploadToShelfService,
    @ApplicationContext private val context: Context
) : RealmRepository(databaseService), UserRepository {
    override suspend fun getUserById(userId: String): RealmUserModel? {
        return withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getUserByAnyId(id: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "_id", id)
            ?: findByField(RealmUserModel::class.java, "id", id)
    }

    override suspend fun getUserByName(name: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "name", name)
    }

    override suspend fun getAllUsers(): List<RealmUserModel> {
        return queryList(RealmUserModel::class.java)
    }

    override suspend fun getUsersSortedBy(fieldName: String, sortOrder: io.realm.Sort): List<RealmUserModel> {
        return queryList(RealmUserModel::class.java) {
            sort(fieldName, sortOrder)
        }
    }

    override suspend fun getMonthlyLoginCounts(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Int> {
        if (startMillis > endMillis) {
            return emptyMap()
        }

        val activities = queryList(RealmOfflineActivity::class.java) {
            equalTo("userId", userId)
            between("loginTime", startMillis, endMillis)
        }

        if (activities.isEmpty()) {
            return emptyMap()
        }

        val calendar = Calendar.getInstance()
        return activities.mapNotNull { it.loginTime }
            .map { loginTime ->
                calendar.timeInMillis = loginTime
                calendar.get(Calendar.MONTH)
            }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
    }

    override suspend fun saveUser(
        jsonDoc: JsonObject?,
        settings: SharedPreferences,
        key: String?,
        iv: String?,
    ): RealmUserModel? {
        if (jsonDoc == null) return null

        return withRealm { realm ->
            val managedUser = populateUsersTable(jsonDoc, realm, settings)
            if (managedUser != null && (key != null || iv != null)) {
                realm.executeTransaction {
                    key?.let { managedUser.key = it }
                    iv?.let { managedUser.iv = it }
                }
            }

            managedUser?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateSecurityData(
        name: String,
        userId: String?,
        rev: String?,
        derivedKey: String?,
        salt: String?,
        passwordScheme: String?,
        iterations: String?,
    ) {
        update(RealmUserModel::class.java, "name", name) { user ->
            user._id = userId
            user._rev = rev
            user.derived_key = derivedKey
            user.salt = salt
            user.password_scheme = passwordScheme
            user.iterations = iterations
            user.isUpdated = false
        }
    }

    override suspend fun updateUserDetails(
        userId: String?,
        firstName: String?,
        lastName: String?,
        middleName: String?,
        email: String?,
        phoneNumber: String?,
        level: String?,
        language: String?,
        gender: String?,
        dob: String?,
    ): RealmUserModel? {
        if (userId.isNullOrBlank()) {
            return null
        }

        update(RealmUserModel::class.java, "id", userId) { user ->
            user.firstName = firstName
            user.lastName = lastName
            user.middleName = middleName
            user.email = email
            user.phoneNumber = phoneNumber
            user.level = level
            user.language = language
            user.gender = gender
            user.dob = dob
            user.isUpdated = true
        }

        return getUserByAnyId(userId)
    }

    override suspend fun updateUserImage(userId: String?, imagePath: String?): RealmUserModel? {
        if (userId.isNullOrBlank()) {
            return null
        }

        update(RealmUserModel::class.java, "id", userId) { user ->
            user.userImage = imagePath
            user.isUpdated = true
        }

        return getUserByAnyId(userId)
    }

    override suspend fun updateProfileFields(userId: String?, payload: JsonObject) {
        if (userId.isNullOrBlank()) {
            return
        }

        update(RealmUserModel::class.java, "id", userId) { model ->
            payload.keySet().forEach { key ->
                when (key) {
                    "firstName" -> model.firstName = payload.get(key).asString
                    "lastName" -> model.lastName = payload.get(key).asString
                    "middleName" -> model.middleName = payload.get(key).asString
                    "email" -> model.email = payload.get(key).asString
                    "language" -> model.language = payload.get(key).asString
                    "phoneNumber" -> model.phoneNumber = payload.get(key).asString
                    "birthDate" -> model.dob = payload.get(key).asString
                    "level" -> model.level = payload.get(key).asString
                    "gender" -> model.gender = payload.get(key).asString
                    "age" -> model.age = payload.get(key).asString
                }
            }
            model.isUpdated = true
        }
    }

    override fun getUserModel(): RealmUserModel? {
        val userId = settings.getString("userId", null)?.takeUnless { it.isBlank() } ?: return null
        return databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .or()
                .equalTo("_id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getUserModelSuspending(): RealmUserModel? {
        val userId = settings.getString("userId", null)?.takeUnless { it.isBlank() } ?: return null
        return withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .or()
                .equalTo("_id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun becomeMember(obj: JsonObject): Pair<Boolean, String> {
        val isAvailable = withContext(Dispatchers.IO) {
            try {
                val response = apiInterface.isPlanetAvailableSuspend(UrlUtils.getUpdateUrl(settings))
                response.code() == 200
            } catch (e: Exception) {
                false
            }
        }

        if (isAvailable) {
            return try {
                val header = UrlUtils.header
                val userName = obj["name"].asString
                val userUrl = "${UrlUtils.getUrl()}/_users/org.couchdb.user:$userName"

                val existsResponse = withContext(Dispatchers.IO) {
                    apiInterface.getJsonObjectSuspended(header, userUrl)
                }

                if (existsResponse.isSuccessful && existsResponse.body()?.has("_id") == true) {
                    Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
                } else {
                    val createResponse = withContext(Dispatchers.IO) {
                        apiInterface.putDocSuspend(null, "application/json", userUrl, obj)
                    }

                    if (createResponse.isSuccessful && createResponse.body()?.has("id") == true) {
                        val id = createResponse.body()?.get("id")?.asString ?: ""

                        // Fire and forget uploadToShelf
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            uploadToShelf(obj)
                        }

                        val result = saveUserToDb(id, obj)
                        if (result.isSuccess) {
                            Pair(true, context.getString(R.string.user_created_successfully))
                        } else {
                            Pair(false, context.getString(R.string.unable_to_save_user_please_sync))
                        }
                    } else {
                        Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
            }
        } else {
            val userName = obj["name"].asString
            val existingUser = getUserByName(userName)
            if (existingUser != null && existingUser._id?.startsWith("guest") != true) {
                return Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
            }

            val keyString = AndroidDecrypter.generateKey()
            val iv = AndroidDecrypter.generateIv()
            saveUser(obj, settings, keyString, iv)
            return Pair(true, context.getString(R.string.not_connect_to_planet_created_user_offline))
        }
    }

    private suspend fun uploadToShelf(obj: JsonObject) {
        try {
            val url = UrlUtils.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString
            apiInterface.putDocSuspend(null, "application/json", url, JsonObject())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveUserToDb(id: String, obj: JsonObject): Result<RealmUserModel?> {
        return try {
            val userModel = withTimeout(20000) {
                val response = apiInterface.getJsonObjectSuspended(
                    UrlUtils.header,
                    "${UrlUtils.getUrl()}/_users/$id"
                )

                ensureActive()

                if (response.isSuccessful) {
                    response.body()?.let { saveUser(it, settings) }
                } else {
                    null
                }
            }

            if (userModel != null) {
                uploadToShelfService.saveKeyIv(apiInterface, userModel, obj)
                Result.success(userModel)
            } else {
                Result.failure(Exception("Failed to save user or user model was null"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override fun getActiveUserId(): String {
        return getUserModel()?.id ?: ""
    }
    override suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: RealmUserModel
    ): HealthRecord? = withRealm { realm ->
        var mh = realm.where(org.ole.planet.myplanet.model.RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = realm.where(org.ole.planet.myplanet.model.RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        if (mh == null) return@withRealm null

        val json = org.ole.planet.myplanet.utilities.AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (android.text.TextUtils.isEmpty(json)) {
            null
        } else {
            try {
                org.ole.planet.myplanet.utilities.GsonUtils.gson.fromJson(json, org.ole.planet.myplanet.model.RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (mm == null) return@withRealm null

        val healths = realm.where(org.ole.planet.myplanet.model.RealmMyHealthPojo::class.java).equalTo("profileId", mm.userKey).findAll()
        val list = realm.copyFromRealm(healths)
        if (list.isEmpty()) {
            return@withRealm HealthRecord(mh, mm, emptyList(), emptyMap())
        }

        val userIds = list.mapNotNull {
            it.getEncryptedDataAsJson(currentUser).let { jsonData ->
                jsonData.get("createdBy")?.asString
            }
        }.distinct()

        val userMap = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            val users = realm.where(RealmUserModel::class.java).`in`("id", userIds.toTypedArray()).findAll()
            realm.copyFromRealm(users).filter { it.id != null }.associateBy { it.id!! }
        }
        HealthRecord(mh, mm, list, userMap)
    }

    override suspend fun searchUsers(query: String): List<RealmUserModel> {
        return withRealm { realm ->
            val results = realm.where(RealmUserModel::class.java)
                .contains("firstName", query, io.realm.Case.INSENSITIVE)
                .or()
                .contains("lastName", query, io.realm.Case.INSENSITIVE)
                .or()
                .contains("name", query, io.realm.Case.INSENSITIVE)
                .sort("joinDate", io.realm.Sort.DESCENDING)
                .findAll()
            realm.copyFromRealm(results)
        }
    }
}
