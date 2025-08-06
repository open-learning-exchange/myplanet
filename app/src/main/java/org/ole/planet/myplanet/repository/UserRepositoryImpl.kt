package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import io.realm.Realm
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel

class UserRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val preferences: SharedPreferences
) : UserRepository {

    override suspend fun getUserProfile(): String? {
        return preferences.getString("user_profile", null)
    }

    override suspend fun saveUserData(data: String) {
        preferences.edit().putString("user_profile", data).apply()
    }

    override suspend fun getCurrentUser(): RealmUserModel? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmUserModel::class.java).findFirst()
        }
    }

    override suspend fun getUserById(userId: String): RealmUserModel? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .findFirst()
        }
    }

    override suspend fun getUserByName(username: String): RealmUserModel? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("name", username)
                .findFirst()
        }
    }

    override suspend fun getAllUsers(): List<RealmUserModel> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmUserModel::class.java).findAll()
        }
    }

    override fun getRealm(): Realm {
        return databaseService.realmInstance
    }
}
