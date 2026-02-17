package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.google.gson.JsonObject
import dagger.Lazy
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
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.RealmUser.Companion.populateUsersTable
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.UrlUtils

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @param:AppPreferences private val settings: SharedPreferences,
    private val apiInterface: ApiInterface,
    private val uploadToShelfService: Lazy<UploadToShelfService>,
    @param:ApplicationContext private val context: Context,
    private val configurationsRepository: ConfigurationsRepository
) : RealmRepository(databaseService), UserRepository {
    override suspend fun getUserById(userId: String): RealmUser? {
        return withRealm { realm ->
            realm.where(RealmUser::class.java)
                .equalTo("id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    @Deprecated("Use getUserModelSuspending() instead")
    override fun getCurrentUser(): RealmUser? {
        return getUserModel()
    }

    override suspend fun getUserByAnyId(id: String): RealmUser? {
        return findByField(RealmUser::class.java, "_id", id)
            ?: findByField(RealmUser::class.java, "id", id)
    }

    override suspend fun getUserByName(name: String): RealmUser? {
        return findByField(RealmUser::class.java, "name", name)
    }

    override suspend fun findUserByName(name: String): RealmUser? {
        return findByField(RealmUser::class.java, "name", name, true)
    }

    override suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser? {
        return withRealm { realm ->
            RealmUser.createGuestUser(username, realm, settings)?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getAllUsers(): List<RealmUser> {
        return queryList(RealmUser::class.java)
    }

    override suspend fun getUsersSortedBy(fieldName: String, sortOrder: io.realm.Sort): List<RealmUser> {
        return queryList(RealmUser::class.java) {
            sort(fieldName, sortOrder)
        }
    }

    override suspend fun getPendingSyncUsers(limit: Int): List<RealmUser> {
        return queryList(RealmUser::class.java) {
            isEmpty("_id").or().equalTo("isUpdated", true)
        }.take(limit)
    }

    override suspend fun searchUsers(query: String, sortField: String, sortOrder: io.realm.Sort): List<RealmUser> {
        return withRealm { realm ->
            val results = realm.where(RealmUser::class.java)
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
    ): RealmUser? {
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

    override suspend fun ensureUserSecurityKeys(userId: String): RealmUser? {
        return withRealm { realm ->
            val user = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
            if (user != null && (user.key == null || user.iv == null)) {
                realm.executeTransaction {
                    if (user.key == null) user.key = AndroidDecrypter.generateKey()
                    if (user.iv == null) user.iv = AndroidDecrypter.generateIv()
                }
            }
            if (user != null) realm.copyFromRealm(user) else null
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
        update(RealmUser::class.java, "name", name) { user ->
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
    ): RealmUser? {
        if (userId.isNullOrBlank()) {
            return null
        }

        update(RealmUser::class.java, "id", userId) { user ->
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

    override suspend fun updateUserImage(userId: String?, imagePath: String?): RealmUser? {
        if (userId.isNullOrBlank()) {
            return null
        }

        update(RealmUser::class.java, "id", userId) { user ->
            user.userImage = imagePath
            user.isUpdated = true
        }

        return getUserByAnyId(userId)
    }

    override suspend fun updateProfileFields(userId: String?, payload: JsonObject) {
        if (userId.isNullOrBlank()) {
            return
        }

        update(RealmUser::class.java, "id", userId) { model ->
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

    @Deprecated("Use getUserModelSuspending() instead")
    override fun getUserModel(): RealmUser? {
        val userId = settings.getString("userId", null)?.takeUnless { it.isBlank() } ?: return null
        return databaseService.withRealm { realm ->
            realm.where(RealmUser::class.java)
                .equalTo("id", userId)
                .or()
                .equalTo("_id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getUserModelSuspending(): RealmUser? {
        val userId = settings.getString("userId", null)?.takeUnless { it.isBlank() } ?: return null
        return withRealm { realm ->
            realm.where(RealmUser::class.java)
                .equalTo("id", userId)
                .or()
                .equalTo("_id", userId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getUserProfile(): RealmUser? {
        val userId = settings.getString("userId", null)?.takeUnless { it.isBlank() } ?: return null
        return queryList(RealmUser::class.java) {
            equalTo("id", userId).or().equalTo("_id", userId)
        }.firstOrNull()
    }

    override suspend fun getUserImageUrl(): String? {
        return getUserProfile()?.userImage
    }

    override suspend fun createMember(user: JsonObject): Pair<Boolean, String> {
        return becomeMember(user)
    }

    override suspend fun becomeMember(obj: JsonObject): Pair<Boolean, String> {
        val userName = obj["name"]?.asString ?: "unknown"

        val isAvailable = configurationsRepository.checkServerAvailability()

        if (isAvailable) {
            return try {
                val header = UrlUtils.header
                val userUrl = "${UrlUtils.getUrl()}/_users/org.couchdb.user:$userName"

                val existsResponse = withContext(Dispatchers.IO) {
                    apiInterface.getJsonObject(header, userUrl)
                }

                if (existsResponse.isSuccessful && existsResponse.body()?.has("_id") == true) {
                    Pair(false, context.getString(R.string.unable_to_create_user_user_already_exists))
                } else {
                    val createResponse = withContext(Dispatchers.IO) {
                        apiInterface.putDoc(null, "application/json", userUrl, obj)
                    }

                    if (createResponse.isSuccessful && createResponse.body()?.has("id") == true) {
                        val id = createResponse.body()?.get("id")?.asString ?: ""

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
            apiInterface.putDoc(null, "application/json", url, JsonObject())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveUserToDb(id: String, obj: JsonObject): Result<RealmUser?> {
        return try {
            val userModel = withTimeout(20000) {
                val response = apiInterface.getJsonObject(
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
                try {
                    uploadToShelfService.get().saveKeyIv(apiInterface, userModel, obj)
                } catch (keyIvException: Exception) { }
                Result.success(userModel)
            } else {
                Result.failure(Exception("Failed to save user or user model was null"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    @Deprecated("Use getActiveUserIdSuspending() instead")
    override fun getActiveUserId(): String {
        return getUserModel()?.id ?: ""
    }

    override suspend fun getActiveUserIdSuspending(): String {
        return getUserModelSuspending()?.id ?: ""
    }
    override suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: RealmUser
    ): HealthRecord? = withRealm { realm ->
        var mh = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
        }
        if (mh == null) return@withRealm null

        val json = AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (TextUtils.isEmpty(json)) {
            null
        } else {
            try {
                JsonUtils.gson.fromJson(json, RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (mm == null) return@withRealm null

        val healths = realm.where(RealmHealthExamination::class.java).equalTo("profileId", mm.userKey).findAll()
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
            val users = realm.where(RealmUser::class.java).`in`("id", userIds.toTypedArray()).findAll()
            realm.copyFromRealm(users).filter { it.id != null }.associateBy { it.id!! }
        }
        HealthRecord(mh, mm, list, userMap)
    }

    override suspend fun getHealthProfile(userId: String): RealmMyHealth? {
        return withRealm { realm ->
            val userModel = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
            val healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                ?: realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()

            if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
                try {
                    val decrypted = AndroidDecrypter.decrypt(healthPojo.data, userModel?.key, userModel?.iv)
                    return@withRealm JsonUtils.gson.fromJson(decrypted, RealmMyHealth::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            null
        }
    }

    override suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>) {
        withRealm { realm ->
            realm.executeTransaction {
                val userModel = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
                val healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                    ?: realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
                    ?: realm.createObject(RealmHealthExamination::class.java, userId)

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
                }

                var myHealth: RealmMyHealth? = null
                if (!TextUtils.isEmpty(healthPojo.data)) {
                    try {
                        val decrypted = AndroidDecrypter.decrypt(healthPojo.data, userModel?.key, userModel?.iv)
                        myHealth = JsonUtils.gson.fromJson(decrypted, RealmMyHealth::class.java)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (myHealth == null) {
                    myHealth = RealmMyHealth()
                }
                if (TextUtils.isEmpty(myHealth.userKey)) {
                    myHealth.userKey = AndroidDecrypter.generateKey()
                }

                val profile = myHealth.profile ?: RealmMyHealthProfile().also { myHealth.profile = it }

                profile.emergencyContactName = (userData["emergencyContactName"] as? String)?.trim() ?: ""
                val newEmergencyContact = (userData["emergencyContact"] as? String)?.trim() ?: ""
                profile.emergencyContact = if (TextUtils.isEmpty(newEmergencyContact)) {
                     profile.emergencyContact
                } else {
                     newEmergencyContact
                }

                val newEmergencyContactType = (userData["emergencyContactType"] as? String)?.trim() ?: ""
                profile.emergencyContactType = if (TextUtils.isEmpty(newEmergencyContactType)) {
                     profile.emergencyContactType
                } else {
                     newEmergencyContactType
                }

                profile.specialNeeds = (userData["specialNeeds"] as? String)?.trim() ?: ""
                profile.notes = (userData["notes"] as? String)?.trim() ?: ""

                healthPojo.userId = userModel?._id
                healthPojo.isUpdated = true

                try {
                    val key = userModel?.key ?: AndroidDecrypter.generateKey().also { newKey -> userModel?.key = newKey }
                    val iv = userModel?.iv ?: AndroidDecrypter.generateIv().also { newIv -> userModel?.iv = newIv }
                    healthPojo.data = AndroidDecrypter.encrypt(JsonUtils.gson.toJson(myHealth), key, iv)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun validateUsername(username: String): String? {
        val specialCharPattern = Pattern.compile(
            ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
        )

        val firstChar = username.firstOrNull()
        when {
            username.isEmpty() -> return context.getString(R.string.username_cannot_be_empty)
            username.contains(" ") -> return context.getString(R.string.invalid_username)
            firstChar != null && !firstChar.isDigit() && !firstChar.isLetter() ->
                return context.getString(R.string.must_start_with_letter_or_number)
            username.any { it != '_' && it != '.' && it != '-' && !it.isDigit() && !it.isLetter() } ||
            specialCharPattern.matcher(username).matches() ||
            !Normalizer.normalize(username, Normalizer.Form.NFD).codePoints().allMatch { code ->
                Character.isLetterOrDigit(code) || code == '.'.code || code == '-'.code || code == '_'.code
            } -> return context.getString(R.string.only_letters_numbers_and_are_allowed)
        }

        val isTaken = withRealm { realm ->
            realm.where(RealmUser::class.java)
                .equalTo("name", username)
                .not().beginsWith("_id", "guest")
                .count() > 0L
        }

        return if (isTaken) context.getString(R.string.username_taken) else null
    }

    override suspend fun cleanupDuplicateUsers() {
        withRealm { realm ->
            val allUsers = realm.where(RealmUser::class.java).findAll()
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
                        sortedUsers[i].deleteFromRealm()
                    }
                }
            }
        }
    }

    override fun authenticateUser(username: String?, password: String?, isManagerMode: Boolean): RealmUser? {
        try {
            val user = databaseService.withRealm { realm ->
                realm.where(RealmUser::class.java).equalTo("name", username).findFirst()?.let { realm.copyFromRealm(it) }
            }
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

    override fun hasAtLeastOneUser(): Boolean {
        return databaseService.withRealm { realm -> realm.where(RealmUser::class.java).findFirst() != null }
    }
}
