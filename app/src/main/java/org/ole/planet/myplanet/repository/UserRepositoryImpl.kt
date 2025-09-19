package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel

class UserRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), UserRepository {
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
