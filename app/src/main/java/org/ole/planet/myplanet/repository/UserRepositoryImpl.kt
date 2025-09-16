package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmUserModel

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val preferences: SharedPreferences
) : RealmRepository(databaseService), UserRepository {

    override suspend fun getUserProfile(): String? {
        return preferences.getString("user_profile", null)
    }

    override suspend fun saveUserData(data: String) {
        preferences.edit().putString("user_profile", data).apply()
    }

    override suspend fun getCurrentUser(): RealmUserModel? {
        return findFirst(RealmUserModel::class.java)
    }

    override suspend fun getUserById(userId: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "id", userId)
    }

    override suspend fun getUserByName(username: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "name", username)
    }

    override suspend fun getAllUsers(): List<RealmUserModel> {
        return queryList(RealmUserModel::class.java)
    }
}
