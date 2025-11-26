package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    @ApplicationContext private val context: Context,
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

    override suspend fun getMyLife(userId: String): List<RealmMyLife> {
        return queryList(RealmMyLife::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun setUpMyLife(userId: String?) {
        if (userId == null) return
        val myLifeList = getMyLife(userId)
        if (myLifeList.isEmpty()) {
            executeTransaction { realm ->
                val myLifeListBase = getMyLifeListBase(userId)
                var weight = 1
                for (item in myLifeListBase) {
                    val ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight
                    ml.userId = item.userId
                    ml.isVisible = true
                    weight++
                }
            }
        }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, context.getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId, context.getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, context.getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, context.getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, context.getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, context.getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, context.getString(R.string.mypersonals)))
        return myLifeList
    }
}
