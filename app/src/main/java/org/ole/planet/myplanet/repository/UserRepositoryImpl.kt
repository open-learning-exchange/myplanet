package org.ole.planet.myplanet.repository

import java.util.Calendar
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUserModel

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), UserRepository {
    override suspend fun getUserById(userId: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "id", userId)
    }

    override suspend fun getUserByAnyId(id: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "_id", id)
            ?: findByField(RealmUserModel::class.java, "id", id)
    }

    override suspend fun getAllUsers(): List<RealmUserModel> {
        return queryList(RealmUserModel::class.java)
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
}
