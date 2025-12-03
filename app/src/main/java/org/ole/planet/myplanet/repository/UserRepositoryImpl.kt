package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonObject
import java.util.Calendar
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
) : RealmRepository(databaseService), UserRepository {
    override suspend fun getUserById(userId: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "id", userId)
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

    override suspend fun getUsers(sortBy: String, sort: io.realm.Sort): List<RealmUserModel> {
        return queryList(RealmUserModel::class.java) {
            sort(sortBy, sort)
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
}
