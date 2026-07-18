package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.Normalizer
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.AchievementDao
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.AchievementData
import org.ole.planet.myplanet.model.DashboardProfile
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MemberInfo
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.MyHealth.MyHealthProfile
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.RetryUtils
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.VersionUtils

class UserRepositoryImpl @Inject constructor(
    @param:AppPreferences private val settings: SharedPreferences,
    private val sharedPrefManager: SharedPrefManager,
    private val apiInterface: ApiInterface,
    private val resourcesRepositoryLazy: dagger.Lazy<ResourcesRepository>,
    private val coursesRepositoryLazy: dagger.Lazy<CoursesRepository>,
    private val uploadToShelfService: Lazy<UploadToShelfService>,
    @param:ApplicationContext private val context: Context,
    private val configurationsRepository: ConfigurationsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val activitiesRepositoryLazy: dagger.Lazy<ActivitiesRepository>,
    private val meetupDao: MeetupDao,
    private val myLibraryDao: org.ole.planet.myplanet.data.room.dao.MyLibraryDao,
    private val offlineActivityDao: OfflineActivityDao,
    private val removedLogDao: RemovedLogDao,
    private val achievementDao: AchievementDao,
    private val healthExaminationDao: HealthExaminationDao,
    private val userDao: UserDao
) : UserRepository, UserSyncRepository {
    override suspend fun getDashboardProfile(userId: String): DashboardProfile {
        val user = getUserById(userId)
        val userName = user?.name
        val fullName = user?.getFullName()?.takeIf { it.trim().isNotBlank() } ?: user?.name

        val count = if (userName != null) {
            activitiesRepositoryLazy.get().getOfflineLoginCount(userName)
        } else {
            0
        }
        return DashboardProfile(fullName, count)
    }

    override suspend fun getUserById(userId: String): UserEntity? {
        return userDao.getById(userId)
    }

    override suspend fun getUsersByIds(userIds: List<String>): List<UserEntity> {
        if (userIds.isEmpty()) return emptyList()
        val userIdSet = userIds.toSet()
        return userDao.getAll()
            .filter { it.id in userIdSet || it._id in userIdSet }
            .map { it }
    }

    override suspend fun getUserByAnyId(id: String): UserEntity? {
        return userDao.getById(id)
    }

    override suspend fun getUserByName(name: String): UserEntity? {
        return userDao.getByName(name)
    }

    override suspend fun findUserByName(name: String): UserEntity? {
        return userDao.getAll()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun getSyncedUsers(): List<UserEntity> {
        return userDao.getAll()
            .filter { !it._id.isNullOrBlank() && !it.id.startsWith("guest") }
            .map { it }
    }

    private fun mapToLightweightUser(managedUser: UserEntity): UserEntity {
        return UserEntity().apply {
            this.id = managedUser.id
            this.name = managedUser.name
            this.planetCode = managedUser.planetCode
        }
    }

    override suspend fun getUsersForHealthSync(): List<UserEntity> {
        return userDao.getAll()
            .asSequence()
            .filter { !it._id.isNullOrBlank() }
            .map { mapToLightweightUser(it) }
            .toList()
    }

    override suspend fun getSyncedUserByName(name: String): UserEntity? {
        return userDao.getByName(name)
            ?.takeIf { !it._id.isNullOrBlank() && !it.id.startsWith("guest") }
    }

    private fun buildGuestUserJson(username: String): JsonObject {
        return JsonObject().apply {
            addProperty("_id", "guest_$username")
            addProperty("name", username)
            addProperty("firstName", username)
            add("roles", JsonArray().apply { add("guest") })
        }
    }

    override suspend fun createGuestUser(username: String): UserEntity? {
        return saveUser(buildGuestUserJson(username))
    }

    override suspend fun getAllUsers(): List<UserEntity> {
        return userDao.getAll().map { it }
    }

    override suspend fun getUsersSortedBy(fieldName: String, descending: Boolean): List<UserEntity> {
        return sortUsers(getAllUsers(), fieldName, descending)
    }

    override suspend fun getPendingSyncUsers(limit: Int): List<UserEntity> {
        return userDao.getAll()
            .asSequence()
            .filter { it._id.isNullOrBlank() || it.isUpdated }
            .map { it }
            .take(limit)
            .toList()
    }

    override suspend fun searchUsers(query: String, sortField: String, descending: Boolean): List<UserEntity> {
        val users = if (query.isBlank()) {
            userDao.getAll()
        } else {
            userDao.search(query)
        }.map { it }
        return sortUsers(users, sortField, descending)
    }

    override suspend fun isUserExists(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return userDao.getByName(name)?.let { !it._id.orEmpty().startsWith("guest") } == true
    }

    override fun parseLeadersJson(jsonString: String): List<UserEntity> {
        val leadersList = mutableListOf<UserEntity>()
        try {
            val jsonObject = org.json.JSONObject(jsonString)
            val docsArray = jsonObject.getJSONArray("docs")
            for (i in 0 until docsArray.length()) {
                val docObject = docsArray.getJSONObject(i)
                val user = UserEntity()
                user.name = docObject.getString("name")
                user.id = if (!docObject.isNull("_id")) {
                    docObject.getString("_id")
                } else {
                    "org.couchdb.user:${user.name}"
                }
                user.rolesList = mutableListOf()
                if (!docObject.isNull("firstName")) {
                    user.firstName = docObject.getString("firstName")
                }
                if (!docObject.isNull("lastName")) {
                    user.lastName = docObject.getString("lastName")
                }
                if (!docObject.isNull("email")) {
                    user.email = docObject.getString("email")
                }
                leadersList.add(user)
            }
        } catch (e: org.json.JSONException) {
            e.printStackTrace()
        }
        return leadersList
    }

    private fun applyJsonToUser(jsonDoc: JsonObject?, user: UserEntity, settings: SharedPreferences) {
        if (jsonDoc == null) return

        val planetCodes = JsonUtils.getString("planetCode", jsonDoc)
        val rolesArray = JsonUtils.getJsonArray("roles", jsonDoc)
        val newId = JsonUtils.getString("_id", jsonDoc)

        user.apply {
            if (id.isNullOrBlank()) {
                id = newId.ifEmpty { UUID.randomUUID().toString() }
            }
            _rev = JsonUtils.getString("_rev", jsonDoc)
            _id = newId
            name = JsonUtils.getString("name", jsonDoc)
            setRoles(mutableListOf<String>().apply {
                for (i in 0 until rolesArray.size()) {
                    add(JsonUtils.getString(rolesArray, i))
                }
            })
            userAdmin = JsonUtils.getBoolean("isUserAdmin", jsonDoc)
            val newJoinDate = JsonUtils.getLong("joinDate", jsonDoc)
            if (newJoinDate != 0L || joinDate == 0L) {
                joinDate = newJoinDate
            }

            val newFirstName = JsonUtils.getString("firstName", jsonDoc)
            if (newFirstName.isNotEmpty() || firstName.isNullOrEmpty()) {
                firstName = newFirstName
            }

            val newLastName = JsonUtils.getString("lastName", jsonDoc)
            if (newLastName.isNotEmpty() || lastName.isNullOrEmpty()) {
                lastName = newLastName
            }

            val newMiddleName = JsonUtils.getString("middleName", jsonDoc)
            if (newMiddleName.isNotEmpty() || middleName.isNullOrEmpty()) {
                middleName = newMiddleName
            }

            val newEmail = JsonUtils.getString("email", jsonDoc)
            if (newEmail.isNotEmpty() || email.isNullOrEmpty()) {
                email = newEmail
            }

            val newPhoneNumber = JsonUtils.getString("phoneNumber", jsonDoc)
            if (newPhoneNumber.isNotEmpty() || phoneNumber.isNullOrEmpty()) {
                phoneNumber = newPhoneNumber
            }

            val newLevel = JsonUtils.getString("level", jsonDoc)
            if (newLevel.isNotEmpty() || level.isNullOrEmpty()) {
                level = newLevel
            }

            val newLanguage = JsonUtils.getString("language", jsonDoc)
            if (newLanguage.isNotEmpty() || language.isNullOrEmpty()) {
                language = newLanguage
            }

            val newGender = JsonUtils.getString("gender", jsonDoc)
            if (newGender.isNotEmpty() || gender.isNullOrEmpty()) {
                gender = newGender
            }

            val newDob = JsonUtils.getString("birthDate", jsonDoc)
            if (newDob.isNotEmpty() || dob.isNullOrEmpty()) {
                dob = newDob
            }

            val newBirthPlace = JsonUtils.getString("birthPlace", jsonDoc)
            if (newBirthPlace.isNotEmpty() || birthPlace.isNullOrEmpty()) {
                birthPlace = newBirthPlace
            }

            val newAge = JsonUtils.getString("age", jsonDoc)
            if (newAge.isNotEmpty() || age.isNullOrEmpty()) {
                age = newAge
            }
            planetCode = planetCodes
            parentCode = JsonUtils.getString("parentCode", jsonDoc)
            if (_id?.isEmpty() == true) {
                password = JsonUtils.getString("password", jsonDoc)
            }
            password_scheme = JsonUtils.getString("password_scheme", jsonDoc)
            iterations = JsonUtils.getString("iterations", jsonDoc)
            derived_key = JsonUtils.getString("derived_key", jsonDoc)
            salt = JsonUtils.getString("salt", jsonDoc)
            isShowTopbar = true
            isArchived = JsonUtils.getBoolean("isArchived", jsonDoc)
            addImageUrl(jsonDoc)
        }

        if (planetCodes.isNotEmpty()) {
            settings.edit { putString("planetCode", planetCodes) }
        }
    }

    private suspend fun migrateGuestUser(id: String, userName: String, users: List<UserEntity>): UserEntity? {
        val guestUser = users.firstOrNull {
            it.name == userName && it._id?.startsWith("guest_") == true
        } ?: return null

        userDao.deleteById(guestUser.id)
        return guestUser.apply {
            this.id = id
            this._id = id
        }
    }

    private suspend fun buildUserFromJson(jsonDoc: JsonObject?, users: List<UserEntity>? = null): UserEntity? {
        if (jsonDoc == null) return null
        return try {
            val availableUsers = users ?: userDao.getAll()
            val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            val userName = JsonUtils.getString("name", jsonDoc)
            val existingUser = availableUsers.firstOrNull { it.id == id || it._id == id }
            val user = existingUser
                ?: if (id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                    migrateGuestUser(id, userName, availableUsers)
                } else {
                    null
                }
                ?: UserEntity().apply { this.id = id }

            applyJsonToUser(jsonDoc, user, settings)
            user
        } catch (err: Exception) {
            err.printStackTrace()
            null
        }
    }

    private suspend fun upsertUser(user: UserEntity): UserEntity? {
        val entity = user ?: return null
        userDao.upsert(entity)
        return userDao.getById(entity.id)
    }

    private fun sortUsers(users: List<UserEntity>, fieldName: String, descending: Boolean): List<UserEntity> {
        fun value(value: String?) = value.orEmpty().lowercase()

        return when (fieldName) {
            "joinDate" -> if (descending) users.sortedByDescending { it.joinDate } else users.sortedBy { it.joinDate }
            "name" -> if (descending) users.sortedByDescending { value(it.name) } else users.sortedBy { value(it.name) }
            "firstName" -> if (descending) users.sortedByDescending { value(it.firstName) } else users.sortedBy { value(it.firstName) }
            "lastName" -> if (descending) users.sortedByDescending { value(it.lastName) } else users.sortedBy { value(it.lastName) }
            "middleName" -> if (descending) users.sortedByDescending { value(it.middleName) } else users.sortedBy { value(it.middleName) }
            "email" -> if (descending) users.sortedByDescending { value(it.email) } else users.sortedBy { value(it.email) }
            "planetCode" -> if (descending) users.sortedByDescending { value(it.planetCode) } else users.sortedBy { value(it.planetCode) }
            "parentCode" -> if (descending) users.sortedByDescending { value(it.parentCode) } else users.sortedBy { value(it.parentCode) }
            "level" -> if (descending) users.sortedByDescending { value(it.level) } else users.sortedBy { value(it.level) }
            "language" -> if (descending) users.sortedByDescending { value(it.language) } else users.sortedBy { value(it.language) }
            "gender" -> if (descending) users.sortedByDescending { value(it.gender) } else users.sortedBy { value(it.gender) }
            "dob" -> if (descending) users.sortedByDescending { value(it.dob) } else users.sortedBy { value(it.dob) }
            "age" -> if (descending) users.sortedByDescending { value(it.age) } else users.sortedBy { value(it.age) }
            "birthPlace" -> if (descending) users.sortedByDescending { value(it.birthPlace) } else users.sortedBy { value(it.birthPlace) }
            "isUpdated" -> if (descending) users.sortedByDescending { it.isUpdated } else users.sortedBy { it.isUpdated }
            "isArchived" -> if (descending) users.sortedByDescending { it.isArchived } else users.sortedBy { it.isArchived }
            else -> users
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

        val activities = offlineActivityDao.getByUserIdAndLoginTimeBetween(userId, startMillis, endMillis)

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
        key: String?,
        iv: String?
    ): UserEntity? {
        if (jsonDoc == null) return null
        val user = buildUserFromJson(jsonDoc) ?: run {
            Log.e("UserRepositoryImpl", "Failed to save user: unable to build user model")
            return null
        }
        key?.let { user.key = it }
        iv?.let { user.iv = it }
        return upsertUser(user)
    }

    override suspend fun fetchUserSecurityData(name: String) {
        try {
            val userDocUrl = "${UrlUtils.getUrl()}/tablet_users/org.couchdb.user:$name"
            val response = withContext(dispatcherProvider.io) {
                apiInterface.getJsonObject(UrlUtils.header, userDocUrl)
            }

            if (response.isSuccessful && response.body() != null) {
                val userDoc = response.body()
                val derivedKey = userDoc?.get("derived_key")?.asString
                val salt = userDoc?.get("salt")?.asString
                val passwordScheme = userDoc?.get("password_scheme")?.asString
                val iterations = userDoc?.get("iterations")?.asString
                val userId = userDoc?.get("_id")?.asString
                val rev = userDoc?.get("_rev")?.asString
                updateSecurityData(name, userId, rev, derivedKey, salt, passwordScheme, iterations)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun ensureUserSecurityKeys(userId: String): UserEntity? {
        val user = getUserById(userId) ?: return null
        if (user.key == null) {
            user.key = AndroidDecrypter.generateKey()
        }
        if (user.iv == null) {
            user.iv = AndroidDecrypter.generateIv()
        }
        return upsertUser(user)
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
        val user = getUserByName(name) ?: return
        user._id = userId
        user._rev = rev
        user.derived_key = derivedKey
        user.salt = salt
        user.password_scheme = passwordScheme
        user.iterations = iterations
        user.isUpdated = false
        upsertUser(user)
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
    ): UserEntity? {
        if (userId.isNullOrBlank()) {
            return null
        }

        val user = getUserByAnyId(userId) ?: return null
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
        return upsertUser(user)
    }

    override suspend fun updateUserImage(userId: String?, imagePath: String?): UserEntity? {
        if (userId.isNullOrBlank()) {
            return null
        }

        val user = getUserByAnyId(userId) ?: return null
        user.userImage = imagePath
        user.isUpdated = true
        return upsertUser(user)
    }

    override suspend fun updateProfileFields(userId: String?, payload: JsonObject) {
        if (userId.isNullOrBlank()) {
            return
        }

        val model = getUserByAnyId(userId) ?: return
        payload.keySet().forEach { key ->
            when (key) {
                "firstName" -> model.firstName = payload.get(key).asString
                "lastName" -> model.lastName = payload.get(key).asString
                "middleName" -> model.middleName = payload.get(key).asString
                "email" -> model.email = payload.get(key).asString
                "language" -> model.language = payload.get(key).asString
                "phoneNumber" -> model.phoneNumber = payload.get(key).asString
                "birthDate" -> model.dob = payload.get(key).asString
                "birthPlace" -> model.birthPlace = payload.get(key).asString
                "level" -> model.level = payload.get(key).asString
                "gender" -> model.gender = payload.get(key).asString
                "age" -> model.age = payload.get(key).asString
            }
        }
        model.isUpdated = true
        upsertUser(model)
    }

    override suspend fun getUserModel(): UserEntity? {
        val userId = sharedPrefManager.getUserId().takeUnless { it.isBlank() } ?: return null
        return userDao.getById(userId)
    }

    override suspend fun getUserProfile(): UserEntity? {
        val userId = sharedPrefManager.getUserId().takeUnless { it.isBlank() } ?: return null
        return userDao.getById(userId)
    }

    override suspend fun getUserImageUrl(): String? {
        return getUserProfile()?.userImage
    }

    override suspend fun createMember(user: MemberInfo): Pair<Boolean, String> {
        val obj = JsonObject().apply {
            addProperty("name", user.username)
            addProperty("firstName", user.fName)
            addProperty("lastName", user.lName)
            addProperty("middleName", user.mName)
            addProperty("password", user.password)
            addProperty("isUserAdmin", false)
            addProperty("joinDate", Calendar.getInstance().timeInMillis)
            addProperty("email", user.email)
            addProperty("planetCode", sharedPrefManager.getPlanetCode())
            addProperty("parentCode", sharedPrefManager.getParentCode())
            addProperty("language", user.language)
            addProperty("level", user.level)
            addProperty("phoneNumber", user.phoneNumber)
            addProperty("birthDate", user.birthDate)
            addProperty("gender", user.gender)
            addProperty("type", "user")
            addProperty("betaEnabled", false)
            addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context))
            addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
            val roles = JsonArray().apply { add("learner") }
            add("roles", roles)
        }
        return becomeMember(obj)
    }

    override suspend fun becomeMember(obj: JsonObject): Pair<Boolean, String> {
        val userName = obj["name"]?.asString ?: "unknown"

        val isAvailable = configurationsRepository.checkServerAvailability()

        if (isAvailable) {
            return try {
                val header = UrlUtils.header
                val userUrl = "${UrlUtils.getUrl()}/_users/org.couchdb.user:$userName"

                val existsResponse = withContext(dispatcherProvider.io) {
                    apiInterface.getJsonObject(header, userUrl)
                }

                if (existsResponse.isSuccessful && existsResponse.body()?.has("_id") == true) {
                    Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
                } else {
                    val createResponse = withContext(dispatcherProvider.io) {
                        apiInterface.putDoc(null, "application/json", userUrl, obj)
                    }

                    if (createResponse.isSuccessful && createResponse.body()?.has("id") == true) {
                        val id = createResponse.body()?.get("id")?.asString ?: ""

                        appScope.launch {
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
            val existingUser = getUserByName(userName)
            if (existingUser != null && existingUser._id?.startsWith("guest") != true) {
                return Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
            }

            val keyString = AndroidDecrypter.generateKey()
            val iv = AndroidDecrypter.generateIv()
            saveUser(obj, keyString, iv)
            return Pair(true, context.getString(R.string.not_connect_to_planet_created_user_offline))
        }
    }

    private suspend fun uploadToShelf(obj: JsonObject) {
        try {
            val url = UrlUtils.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString
            apiInterface.putDoc(null, "application/json", url, JsonObject())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveUserToDb(id: String, obj: JsonObject): Result<UserEntity?> {
        return try {
            val userModel = withTimeout(20000) {
                val response = apiInterface.getJsonObject(
                    UrlUtils.header,
                    "${UrlUtils.getUrl()}/_users/$id"
                )

                ensureActive()

                if (response.isSuccessful) {
                    response.body()?.let { saveUser(it, null, null) }
                } else {
                    null
                }
            }

            if (userModel != null) {
                try {
                    saveKeyIv(userModel, obj)
                } catch (_: Exception) { }
                Result.success(userModel)
            } else {
                Result.failure(Exception("Failed to save user or user model was null"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }


    override suspend fun changeUserSecurity(model: UserEntity, obj: JsonObject) {
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

    override suspend fun saveKeyIv(model: UserEntity, obj: JsonObject) {
        val table = "userdb-${Utilities.toHex(model.planetCode)}-${Utilities.toHex(model.name)}"
        val header = "Basic ${Base64.encodeToString(("${obj["name"].asString}:${obj["password"].asString}").toByteArray(), Base64.NO_WRAP)}"
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

            markUserKeyIvSaved(model.id ?: "", keyString ?: "", iv)
        } else {
            throw IOException("Failed to save key/IV after $maxAttempts attempts")
        }
    }

    private fun replacedUrl(model: UserEntity): String {
        val url = UrlUtils.getUrl()
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val replacedUrl = url.replaceFirst("[^:]+:[^@]+@".toRegex(), "${model.name}:${password}@")
        val protocolIndex = url.indexOf("://")
        val protocol = url.substring(0, protocolIndex)
        return "$protocol://$replacedUrl"
    }

    override suspend fun checkIfUserExists(header: String, model: UserEntity): Boolean {
        try {
            val res = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")
            val exists = res.body() != null
            return exists
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override suspend fun processUserAfterCreation(model: UserEntity, obj: JsonObject, updateHealthFn: suspend (String, String) -> Unit) {
        try {
            val password = model.password ?: SecurePrefs.getPassword(context, settings) ?: ""
            val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
            val fetchDataResponse = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/${model._id}")

            if (fetchDataResponse.isSuccessful) {
                val passwordScheme = JsonUtils.getString("password_scheme", fetchDataResponse.body())
                val derivedKey = JsonUtils.getString("derived_key", fetchDataResponse.body())
                val salt = JsonUtils.getString("salt", fetchDataResponse.body())
                val iterations = JsonUtils.getString("iterations", fetchDataResponse.body())

                model.password_scheme = passwordScheme
                model.derived_key = derivedKey
                model.salt = salt
                model.iterations = iterations

                updateSecurityData(
                    model.name ?: "",
                    model._id,
                    model._rev,
                    derivedKey,
                    salt,
                    passwordScheme,
                    iterations
                )

                saveKeyIv(model, obj)

                updateHealthFn(model.id ?: "", model._id ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun uploadNewUser(model: UserEntity, updateHealthFn: suspend (String, String) -> Unit) {
        try {
            val obj = model.serialize()
            val createResponse = apiInterface.putDoc(null, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", obj)

            if (createResponse.isSuccessful) {
                val id = createResponse.body()?.get("id")?.asString
                val rev = createResponse.body()?.get("rev")?.asString
                model._id = id
                model._rev = rev

                // Persist _id and _rev to database
                markUserUploaded(model.id ?: "", id ?: "", rev ?: "")

                processUserAfterCreation(model, obj, updateHealthFn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateExistingUser(header: String, model: UserEntity) {
        try {
            val latestDocResponse = apiInterface.getJsonObject(header, "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}")

            if (latestDocResponse.isSuccessful) {
                val latestRev = latestDocResponse.body()?.get("_rev")?.asString
                val obj = model.serialize()
                val objMap = obj.entrySet().associate { (key, value) -> key to value }
                val mutableObj = mutableMapOf<String, Any>().apply { putAll(objMap) }
                latestRev?.let { rev -> mutableObj["_rev"] = rev as Any }

                val jsonElement = JsonUtils.gson.toJsonTree(mutableObj)
                val jsonObject = jsonElement.asJsonObject

                val updateResponse = apiInterface.putDoc(header, "application/json", "${replacedUrl(model)}/_users/org.couchdb.user:${model.name}", jsonObject)

                if (updateResponse.isSuccessful) {
                    val updatedRev = updateResponse.body()?.get("rev")?.asString
                    markUserRevUpdated(model.id ?: "", updatedRev)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getActiveUserIdSuspending(): String {
        return getUserModel()?.id ?: ""
    }

    override suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: UserEntity
    ): HealthRecord? {
        val mh = healthExaminationDao.getByIdOrUserId(userId) ?: return null
        val json = AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (TextUtils.isEmpty(json)) {
            null
        } else {
            try {
                JsonUtils.gson.fromJson(json, MyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: return null

        val list = healthExaminationDao.getByProfileId(mm.userKey ?: "")
        if (list.isEmpty()) {
            return HealthRecord(mh, mm, emptyList(), emptyMap())
        }

        val userIds = list.mapNotNull {
            it.getEncryptedDataAsJson(currentUser).let { jsonData ->
                jsonData.get("createdBy")?.asString
            }
        }.distinct()

        val userMap = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            val userIdSet = userIds.toSet()
            userDao.getAll()
                .filter { it.id in userIdSet }
                .map { it }
                .associateBy { it.id ?: "" }
        }
        return HealthRecord(mh, mm, list, userMap)
    }

    override suspend fun getHealthProfile(userId: String): MyHealth? {
        val userModel = getUserByAnyId(userId)
        val healthPojo = healthExaminationDao.getByIdOrUserId(userId)

        if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
            try {
                val decrypted = AndroidDecrypter.decrypt(healthPojo.data, userModel?.key, userModel?.iv)
                return JsonUtils.gson.fromJson(decrypted, MyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    override suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>) {
        val userModel = getUserByAnyId(userId)
        val healthPojo = healthExaminationDao.getByIdOrUserId(userId) ?: HealthExamination().apply { _id = userId }

        userModel?.apply {
            firstName = (userData["firstName"] as? String)?.trim()
            middleName = (userData["middleName"] as? String)?.trim()
            lastName = (userData["lastName"] as? String)?.trim()
            email = (userData["email"] as? String)?.trim()
            phoneNumber = (userData["phoneNumber"] as? String)?.trim()
            birthPlace = (userData["birthPlace"] as? String)?.trim()
            userData["dob"]?.let { dobVal ->
                val dobInput = (dobVal as String).trim()
                dob = TimeUtils.convertDDMMYYYYToISO(dobInput)
            }
            isUpdated = true
            upsertUser(this)
        }

        var myHealth: MyHealth? = null
        if (!TextUtils.isEmpty(healthPojo.data)) {
            try {
                val decrypted = AndroidDecrypter.decrypt(healthPojo.data, userModel?.key, userModel?.iv)
                myHealth = JsonUtils.gson.fromJson(decrypted, MyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (myHealth == null) {
            myHealth = MyHealth()
        }
        if (TextUtils.isEmpty(myHealth.userKey)) {
            myHealth.userKey = AndroidDecrypter.generateKey()
        }

        val profile = myHealth.profile ?: MyHealthProfile().also { myHealth.profile = it }

        profile.emergencyContactName = (userData["emergencyContactName"] as? String)?.trim() ?: ""
        val newEmergencyContact = (userData["emergencyContact"] as? String)?.trim() ?: ""
        profile.emergencyContact = if (TextUtils.isEmpty(newEmergencyContact)) profile.emergencyContact else newEmergencyContact

        val newEmergencyContactType = (userData["emergencyContactType"] as? String)?.trim() ?: ""
        profile.emergencyContactType = if (TextUtils.isEmpty(newEmergencyContactType)) profile.emergencyContactType else newEmergencyContactType

        profile.specialNeeds = (userData["specialNeeds"] as? String)?.trim() ?: ""
        profile.notes = (userData["notes"] as? String)?.trim() ?: ""

        healthPojo.userId = userModel?._id
        healthPojo.isUpdated = true

        try {
            val key = userModel?.key ?: AndroidDecrypter.generateKey().also { newKey -> userModel?.key = newKey }
            val iv = userModel?.iv ?: AndroidDecrypter.generateIv().also { newIv -> userModel?.iv = newIv }
            healthPojo.data = AndroidDecrypter.encrypt(JsonUtils.gson.toJson(myHealth), key, iv)
            userModel?.let { upsertUser(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        healthExaminationDao.upsert(healthPojo)
    }

    override suspend fun validateUsername(username: String): String? {
        val firstChar = username.firstOrNull()
        when {
            username.isEmpty() -> return context.getString(R.string.username_cannot_be_empty)
            username.contains(" ") -> return context.getString(R.string.invalid_username)
            firstChar != null && !firstChar.isDigit() && !firstChar.isLetter() ->
                return context.getString(R.string.must_start_with_letter_or_number)
            username.any { it != '_' && it != '.' && it != '-' && !it.isDigit() && !it.isLetter() } ||
            SPECIAL_CHAR_PATTERN.matcher(username).matches() ||
            !Normalizer.normalize(username, Normalizer.Form.NFD).codePoints().allMatch { code ->
                Character.isLetterOrDigit(code) || code == '.'.code || code == '-'.code || code == '_'.code
            } -> return context.getString(R.string.only_letters_numbers_and_are_allowed)
        }

        val isTaken = userDao.getByName(username)?.let { !it._id.orEmpty().startsWith("guest") } == true

        return if (isTaken) context.getString(R.string.username_taken) else null
    }

    override suspend fun cleanupDuplicateUsers() {
        val allUsers = userDao.getAll()
        val usersByName = allUsers.groupBy { it.name }

        usersByName.forEach { (_, users) ->
            if (users.size > 1) {
                val sortedUsers = users.sortedWith { user1, user2 ->
                    when {
                        user1._id?.startsWith("org.couchdb.user:") == true &&
                            user2._id?.startsWith("guest_") == true -> -1
                        user1._id?.startsWith("guest_") == true &&
                            user2._id?.startsWith("org.couchdb.user:") == true -> 1
                        else -> 0
                    }
                }

                for (i in 1 until sortedUsers.size) {
                    userDao.deleteById(sortedUsers[i].id)
                }
            }
        }
    }

    override suspend fun authenticateUser(username: String?, password: String?, isManagerMode: Boolean): UserEntity? {
        try {
            val user = if (username != null) getUserByName(username) else null
            user?.let {
                if (it._id?.isEmpty() == true) {
                    if (username == it.name && password == it.password) {
                        return it
                    }
                } else {
                    if (AndroidDecrypter.androidDecrypter(username, password, it.derived_key, it.salt)) {
                        if (isManagerMode && !it.isManager()) return null
                        return it
                    }
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
            return null
        }
        return null
    }

    override suspend fun hasAtLeastOneUser(): Boolean {
        return userDao.count() > 0
    }

    override suspend fun hasUserSyncAction(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        return activitiesRepositoryLazy.get().hasUserSyncAction(userId)
    }

    override suspend fun initializeAchievement(achievementId: String): Achievement? {
        val existing = achievementDao.getById(achievementId)
        if (existing != null) return existing
        val achievement = Achievement().apply { _id = achievementId }
        achievementDao.upsert(achievement)
        return achievement
    }

    override suspend fun updateAchievement(
        achievementId: String,
        header: String,
        goals: String,
        purpose: String,
        sendToNation: String,
        achievements: JsonArray,
        references: JsonArray,
        createdOn: String,
        username: String,
        parentCode: String,
        resumeFileName: String
    ) {
        val achievement = achievementDao.getById(achievementId) ?: return
        achievement.achievementsHeader = header
        achievement.goals = goals
        achievement.purpose = purpose
        achievement.sendToNation = sendToNation
        achievement.createdOn = createdOn
        achievement.username = username
        achievement.parentCode = parentCode
        achievement.setAchievements(achievements)
        achievement.setReferences(references)
        achievement.resumeFileName = resumeFileName
        achievement.isUpdated = true
        achievementDao.upsert(achievement)
    }

    override suspend fun markUserUploaded(userId: String, id: String, rev: String) {
        val user = getUserById(userId) ?: return
        user._id = id
        user._rev = rev
        upsertUser(user)
    }

    override suspend fun markUserKeyIvSaved(userId: String, key: String, iv: String?) {
        val user = getUserById(userId) ?: return
        user.key = key
        user.iv = iv
        upsertUser(user)
    }

    override suspend fun markUserRevUpdated(userId: String, rev: String?) {
        val user = getUserById(userId) ?: return
        user._rev = rev
        user.isUpdated = false
        upsertUser(user)
    }

    companion object {
        private val SPECIAL_CHAR_PATTERN = Pattern.compile(
            ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
        )
    }

    override suspend fun getAchievementData(userId: String, planetCode: String): AchievementData {
        val achievement = achievementDao.getById("$userId@$planetCode") ?: return AchievementData()
        val resourceIds = achievement.achievements?.mapNotNull { json ->
            JsonUtils.gson.fromJson(json, JsonObject::class.java)
                ?.getAsJsonArray("resources")
                ?.mapNotNull { it.asJsonObject?.get("_id")?.asString }
        }?.flatten()?.distinct()?.toTypedArray() ?: emptyArray()

        val resources = if (resourceIds.isNotEmpty()) {
            myLibraryDao.getByIds(resourceIds.toList())
        } else {
            emptyList()
        }

        return AchievementData(
            goals = achievement.goals ?: "",
            purpose = achievement.purpose ?: "",
            achievementsHeader = achievement.achievementsHeader ?: "",
            achievements = achievement.achievements ?: emptyList(),
            achievementResources = resources,
            references = achievement.references ?: emptyList(),
            resumeFileName = achievement.resumeFileName ?: ""
        )
    }

    override suspend fun getAchievementsForUpload(): List<JsonObject> {
        return achievementDao.getPendingUploads().map { Achievement.serialize(it) }
    }

    override suspend fun getSavedUsers(): List<User> = sharedPrefManager.getSavedUsers()

    override suspend fun upsertSavedUser(name: String?, encryptedPassword: String?, source: String, userProfile: String?, userName: String?) {
        val existingUsers: MutableList<User> = ArrayList(sharedPrefManager.getSavedUsers())
        if (source == "guest") {
            val newUser = User("", name, encryptedPassword, "", "guest")
            var newUserIndex = -1
            for (i in existingUsers.indices) {
                if (existingUsers[i].name == newUser.name?.trim { it <= ' ' }) {
                    newUserIndex = i
                    break
                }
            }
            if (newUserIndex != -1) {
                existingUsers[newUserIndex] = newUser
            } else {
                existingUsers.add(newUser)
            }
            sharedPrefManager.setSavedUsers(existingUsers)
        } else if (source == "member") {
            val newUser = User(userName, name, encryptedPassword, userProfile, "member")
            var newUserIndex = -1
            for (i in existingUsers.indices) {
                if (existingUsers[i].fullName == newUser.fullName?.trim { it <= ' ' }) {
                    newUserIndex = i
                    break
                }
            }
            if (newUserIndex != -1) {
                existingUsers[newUserIndex] = newUser
            } else {
                existingUsers.add(newUser)
            }
            sharedPrefManager.setSavedUsers(existingUsers)
        }
    }

    override suspend fun resetGuestAsMember(username: String?) {
        val existingUsers = sharedPrefManager.getSavedUsers().toMutableList()
        var newUserExists = false
        for ((_, name) in existingUsers) {
            if (name == username) {
                newUserExists = true
                break
            }
        }
        if (newUserExists) {
            val iterator = existingUsers.iterator()
            while (iterator.hasNext()) {
                val (_, name) = iterator.next()
                if (name == username) {
                    iterator.remove()
                }
            }
            sharedPrefManager.setSavedUsers(existingUsers)
        }
    }

    override suspend fun markAchievementUploaded(id: String, rev: String?) {
        achievementDao.markUploaded(id, rev)
    }

    override suspend fun bulkInsertAchievementsFromSync(jsonArray: JsonArray) {
        val achievements = ArrayList<Achievement>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                achievements.add(Achievement.fromJson(jsonDoc))
            }
        }
        achievementDao.upsertAll(achievements)
    }

    override suspend fun insertUsersFromSync(docs: List<JsonObject>) {
        val documentList = ArrayList<JsonObject>(docs.size)
        for (j in docs) {
            var jsonDoc = j
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }

        val existingUsers = userDao.getAll().toMutableList()
        val usersToDelete = linkedSetOf<String>()
        val usersToUpsert = mutableListOf<UserEntity>()

        for (jsonDoc in documentList) {
            try {
                val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
                val userName = JsonUtils.getString("name", jsonDoc)
                val existingUser = existingUsers.firstOrNull { it.id == id || it._id == id }
                val guestUser = if (existingUser == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                    existingUsers.firstOrNull { it.name == userName && it._id?.startsWith("guest_") == true }
                } else {
                    null
                }

                val user = existingUser
                    ?: guestUser?.apply {
                        usersToDelete += guestUser.id
                        this.id = id
                        this._id = id
                    }
                    ?: UserEntity().apply { this.id = id }

                applyJsonToUser(jsonDoc, user, settings)
                val entity = user ?: continue
                usersToUpsert.removeAll { it.id == entity.id }
                usersToUpsert += entity
                existingUsers.removeAll { it.id == entity.id || it._id == entity._id }
                existingUsers += entity
            } catch (err: Exception) {
                err.printStackTrace()
            }
        }

        usersToDelete.forEach { userDao.deleteById(it) }
        if (usersToUpsert.isNotEmpty()) {
            userDao.upsertAll(usersToUpsert)
        }
    }


    override suspend fun uploadShelfData(user: UserEntity) {
        try {
            val jsonDoc = apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/${user._id}").body()
            val myLibs = resourcesRepositoryLazy.get().getMyLibIds(user.id ?: "")
            val myCourseIds = coursesRepositoryLazy.get().getMyCourseIds(user.id ?: "")
            val shelfData = getShelfData(user.id, jsonDoc, myLibs, myCourseIds)
            shelfData.addProperty("_rev", JsonUtils.getString("_rev", jsonDoc))
            apiInterface.putDoc(
                UrlUtils.header,
                "application/json",
                "${UrlUtils.getUrl()}/shelf/${user._id}",
                shelfData
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override suspend fun checkShelfBatchForDataOptimized(shelfIds: List<String>): List<String> {
        val shelvesWithData = mutableListOf<String>()
        val keysObject = JsonObject().apply {
            add("keys", com.google.gson.Gson().fromJson(com.google.gson.Gson().toJson(shelfIds), JsonArray::class.java))
        }

        val response = org.ole.planet.myplanet.data.api.ApiClient.executeWithRetryAndWrap {
            apiInterface.findDocs(org.ole.planet.myplanet.utils.UrlUtils.header, "application/json", "${org.ole.planet.myplanet.utils.UrlUtils.getUrl()}/shelf/_all_docs?include_docs=true", keysObject)
        }?.body()

        response?.let { responseBody ->
            val rows = org.ole.planet.myplanet.utils.JsonUtils.getJsonArray("rows", responseBody)
            for (i in 0 until rows.size()) {
                val row = rows[i].asJsonObject
                if (row.has("doc")) {
                    val doc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", row)
                    val shelfId = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", doc)

                    if (hasShelfDataUltraFast(doc)) {
                        shelvesWithData.add(shelfId)
                    }
                }
            }
        }
        return shelvesWithData
    }

    private fun hasShelfDataUltraFast(shelfDoc: JsonObject): Boolean {
        return listOf("resourceIds", "courseIds", "meetupIds", "teamIds").any { key ->
            shelfDoc.has(key) && shelfDoc.get(key).let { element ->
                element.isJsonArray && element.asJsonArray.size() > 0
            }
        }
    }

    private suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject {
        val userMeetups = if (userId.isNullOrBlank()) {
            emptyList()
        } else {
            meetupDao.getByUserId(userId)
        }
        val myMeetups = Meetup.getMyMeetUpIds(userMeetups)
        val removedResources = removedLogDao.getRemovedDocIds("resources", userId).filterNotNull()
        val removedCourses = removedLogDao.getRemovedDocIds("courses", userId).filterNotNull()
        val mergedResourceIds = mergeJsonArray(myLibs, JsonUtils.getJsonArray("resourceIds", jsonDoc), removedResources)
        val mergedCourseIds = mergeJsonArray(myCourseIds, JsonUtils.getJsonArray("courseIds", jsonDoc), removedCourses)
        val `object` = JsonObject()
        `object`.addProperty("_id", sharedPrefManager.getUserId())
        `object`.add("meetupIds", mergeJsonArray(myMeetups, JsonUtils.getJsonArray("meetupIds", jsonDoc), removedResources))
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
}
