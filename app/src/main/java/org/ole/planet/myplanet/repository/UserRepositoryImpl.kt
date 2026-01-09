package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Calendar
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    private val apiInterface: ApiInterface,
    private val uploadToShelfService: UploadToShelfService,
    @ApplicationContext private val context: Context
) : RealmRepository(databaseService), UserRepository {
    companion object {
        private const val TAG = "BECOME_MEMBER"
    }
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

    override suspend fun searchUsers(query: String, sortField: String, sortOrder: io.realm.Sort): List<RealmUserModel> {
        return withRealm { realm ->
            val results = realm.where(RealmUserModel::class.java)
                .contains("firstName", query, io.realm.Case.INSENSITIVE).or()
                .contains("lastName", query, io.realm.Case.INSENSITIVE).or()
                .contains("name", query, io.realm.Case.INSENSITIVE)
                .sort(sortField, sortOrder).findAll()
            realm.copyFromRealm(results)
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
        val userName = obj["name"]?.asString ?: "unknown"
        Log.d(TAG, "[Repository] becomeMember started for username: $userName")

        val isAvailable = withContext(Dispatchers.IO) {
            try {
                val updateUrl = UrlUtils.getUpdateUrl(settings)
                Log.d(TAG, "[Repository] Checking server availability at: $updateUrl")
                val response = apiInterface.isPlanetAvailableSuspend(updateUrl)
                val available = response.code() == 200
                Log.d(TAG, "[Repository] Server available: $available (response code: ${response.code()})")
                available
            } catch (e: Exception) {
                Log.e(TAG, "[Repository] Server availability check failed: ${e.message}", e)
                false
            }
        }

        if (isAvailable) {
            Log.d(TAG, "[Repository] Server is online, proceeding with online user creation for: $userName")
            return try {
                val header = UrlUtils.header
                val userUrl = "${UrlUtils.getUrl()}/_users/org.couchdb.user:$userName"
                Log.d(TAG, "[Repository] User URL: $userUrl")

                Log.d(TAG, "[Repository] Checking if user already exists: $userName")
                val existsResponse = withContext(Dispatchers.IO) {
                    apiInterface.getJsonObjectSuspended(header, userUrl)
                }
                Log.d(TAG, "[Repository] Check user exists response - successful: ${existsResponse.isSuccessful}, code: ${existsResponse.code()}")

                if (existsResponse.isSuccessful && existsResponse.body()?.has("_id") == true) {
                    Log.w(TAG, "[Repository] User already exists: $userName")
                    Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
                } else {
                    Log.d(TAG, "[Repository] User does not exist, creating new user: $userName")
                    val createResponse = withContext(Dispatchers.IO) {
                        apiInterface.putDocSuspend(null, "application/json", userUrl, obj)
                    }
                    Log.d(TAG, "[Repository] Create user response - successful: ${createResponse.isSuccessful}, code: ${createResponse.code()}")

                    if (createResponse.isSuccessful && createResponse.body()?.has("id") == true) {
                        val id = createResponse.body()?.get("id")?.asString ?: ""
                        Log.d(TAG, "[Repository] User created successfully on server with ID: $id")

                        // Fire and forget uploadToShelf
                        Log.d(TAG, "[Repository] Starting uploadToShelf in background for: $userName")
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            uploadToShelf(obj)
                        }

                        Log.d(TAG, "[Repository] Saving user to local database with ID: $id")
                        val result = saveUserToDb(id, obj)
                        if (result.isSuccess) {
                            Log.d(TAG, "[Repository] User saved to database successfully for: $userName")
                            Pair(true, context.getString(R.string.user_created_successfully))
                        } else {
                            Log.e(TAG, "[Repository] Failed to save user to database for: $userName")
                            Pair(false, context.getString(R.string.unable_to_save_user_please_sync))
                        }
                    } else {
                        Log.e(TAG, "[Repository] Failed to create user on server for: $userName, response code: ${createResponse.code()}")
                        Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Repository] Exception during online user creation for: $userName - ${e.message}", e)
                e.printStackTrace()
                Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
            }
        } else {
            Log.d(TAG, "[Repository] Server is offline, proceeding with offline user creation for: $userName")
            val existingUser = getUserByName(userName)
            if (existingUser != null && existingUser._id?.startsWith("guest") != true) {
                Log.w(TAG, "[Repository] User already exists locally (not a guest): $userName")
                return Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
            }

            Log.d(TAG, "[Repository] Generating encryption keys for offline user: $userName")
            val keyString = AndroidDecrypter.generateKey()
            val iv = AndroidDecrypter.generateIv()
            Log.d(TAG, "[Repository] Saving user offline with generated keys for: $userName")
            saveUser(obj, settings, keyString, iv)
            Log.d(TAG, "[Repository] Offline user created successfully for: $userName")
            return Pair(true, context.getString(R.string.not_connect_to_planet_created_user_offline))
        }
    }

    private suspend fun uploadToShelf(obj: JsonObject) {
        val userName = obj["name"]?.asString ?: "unknown"
        try {
            val url = UrlUtils.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString
            Log.d(TAG, "[Repository] uploadToShelf started for username: $userName, URL: $url")
            val response = apiInterface.putDocSuspend(null, "application/json", url, JsonObject())
            Log.d(TAG, "[Repository] uploadToShelf completed for username: $userName, successful: ${response.isSuccessful}, code: ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "[Repository] uploadToShelf failed for username: $userName - ${e.message}", e)
            e.printStackTrace()
        }
    }

    private suspend fun saveUserToDb(id: String, obj: JsonObject): Result<RealmUserModel?> {
        val userName = obj["name"]?.asString ?: "unknown"
        Log.d(TAG, "[Repository] saveUserToDb started for username: $userName, ID: $id")
        return try {
            val userModel = withTimeout(20000) {
                val url = "${UrlUtils.getUrl()}/_users/$id"
                Log.d(TAG, "[Repository] Fetching user data from server: $url")
                val response = apiInterface.getJsonObjectSuspended(
                    UrlUtils.header,
                    url
                )
                Log.d(TAG, "[Repository] Fetch user data response - successful: ${response.isSuccessful}, code: ${response.code()}")

                ensureActive()

                if (response.isSuccessful) {
                    Log.d(TAG, "[Repository] Saving user to local database for username: $userName")
                    response.body()?.let { saveUser(it, settings) }
                } else {
                    Log.e(TAG, "[Repository] Failed to fetch user data from server for ID: $id")
                    null
                }
            }

            if (userModel != null) {
                Log.d(TAG, "[Repository] User saved to database, saving key/IV for username: $userName")
                uploadToShelfService.saveKeyIv(apiInterface, userModel, obj)
                Log.d(TAG, "[Repository] saveUserToDb completed successfully for username: $userName")
                Result.success(userModel)
            } else {
                Log.e(TAG, "[Repository] saveUserToDb failed - user model is null for username: $userName")
                Result.failure(Exception("Failed to save user or user model was null"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Repository] saveUserToDb exception for username: $userName - ${e.message}", e)
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
        var mh = realm.where(org.ole.planet.myplanet.model.RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = realm.where(org.ole.planet.myplanet.model.RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
        }
        if (mh == null) return@withRealm null

        val json = org.ole.planet.myplanet.utilities.AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (android.text.TextUtils.isEmpty(json)) {
            null
        } else {
            try {
                org.ole.planet.myplanet.utilities.JsonUtils.gson.fromJson(json, org.ole.planet.myplanet.model.RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (mm == null) return@withRealm null

        val healths = realm.where(org.ole.planet.myplanet.model.RealmHealthExamination::class.java).equalTo("profileId", mm.userKey).findAll()
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

    override suspend fun validateUsername(username: String): String? {
        Log.d(TAG, "[Repository] validateUsername called for: $username")
        val specialCharPattern = Pattern.compile(
            ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
        )

        val firstChar = username.firstOrNull()
        when {
            username.isEmpty() -> {
                Log.w(TAG, "[Repository] Username validation failed: empty username")
                return context.getString(R.string.username_cannot_be_empty)
            }
            username.contains(" ") -> {
                Log.w(TAG, "[Repository] Username validation failed: contains spaces - $username")
                return context.getString(R.string.invalid_username)
            }
            firstChar != null && !firstChar.isDigit() && !firstChar.isLetter() -> {
                Log.w(TAG, "[Repository] Username validation failed: doesn't start with letter or number - $username")
                return context.getString(R.string.must_start_with_letter_or_number)
            }
            username.any { it != '_' && it != '.' && it != '-' && !it.isDigit() && !it.isLetter() } ||
            specialCharPattern.matcher(username).matches() ||
            !Normalizer.normalize(username, Normalizer.Form.NFD).codePoints().allMatch { code ->
                Character.isLetterOrDigit(code) || code == '.'.code || code == '-'.code || code == '_'.code
            } -> {
                Log.w(TAG, "[Repository] Username validation failed: invalid characters - $username")
                return context.getString(R.string.only_letters_numbers_and_are_allowed)
            }
        }

        Log.d(TAG, "[Repository] Checking if username is already taken: $username")
        val isTaken = withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("name", username)
                .not().beginsWith("_id", "guest")
                .count() > 0L
        }
        Log.d(TAG, "[Repository] Username taken check result for $username: $isTaken")

        return if (isTaken) {
            Log.w(TAG, "[Repository] Username validation failed: username taken - $username")
            context.getString(R.string.username_taken)
        } else {
            Log.d(TAG, "[Repository] Username validation passed for: $username")
            null
        }
    }

    override suspend fun cleanupDuplicateUsers() {
        Log.d(TAG, "[Repository] cleanupDuplicateUsers started")
        withRealm { realm ->
            val allUsers = realm.where(RealmUserModel::class.java).findAll()
            Log.d(TAG, "[Repository] Found ${allUsers.size} total users in database")
            val usersByName = allUsers.groupBy { it.name }
            Log.d(TAG, "[Repository] Grouped into ${usersByName.size} unique usernames")

            var duplicatesRemoved = 0
            usersByName.forEach { (name, users) ->
                if (users.size > 1) {
                    Log.d(TAG, "[Repository] Found ${users.size} duplicate users for username: $name")
                    val sortedUsers = users.sortedWith { user1, user2 ->
                        when {
                            user1._id?.startsWith("org.couchdb.user:") == true &&
                                    user2._id?.startsWith("guest_") == true -> -1
                            user1._id?.startsWith("guest_") == true &&
                                    user2._id?.startsWith("org.couchdb.user:") == true -> 1
                            else -> 0
                        }
                    }

                    Log.d(TAG, "[Repository] Keeping user with ID: ${sortedUsers[0]._id}, removing ${sortedUsers.size - 1} duplicates")
                    for (i in 1 until sortedUsers.size) {
                        Log.d(TAG, "[Repository] Removing duplicate user with ID: ${sortedUsers[i]._id}")
                        sortedUsers[i].deleteFromRealm()
                        duplicatesRemoved++
                    }
                }
            }
            Log.d(TAG, "[Repository] cleanupDuplicateUsers completed - removed $duplicatesRemoved duplicate users")
        }
    }
}
