package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Sort
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import javax.inject.Inject

class HealthRepository @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService) {

    fun getSortedUsers(sortBy: String, sort: Sort): List<RealmUserModel> {
        val realm = databaseService.realmInstance
        return realm.where(RealmUserModel::class.java).sort(sortBy, sort).findAll()
    }

    fun searchUsers(query: String): List<RealmUserModel> {
        val realm = databaseService.realmInstance
        return realm.where(RealmUserModel::class.java)
            .contains("firstName", query, Case.INSENSITIVE).or()
            .contains("lastName", query, Case.INSENSITIVE).or()
            .contains("name", query, Case.INSENSITIVE)
            .sort("joinDate", Sort.DESCENDING).findAll()
    }

    fun getHealthPojo(userId: String?): RealmMyHealthPojo? {
        if (userId.isNullOrEmpty()) return null
        val realm = databaseService.realmInstance
        var mh = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        return mh
    }

    fun getExaminations(userKey: String?): List<RealmMyHealthPojo> {
        val realm = databaseService.realmInstance
        return realm.where(RealmMyHealthPojo::class.java).equalTo("profileId", userKey).findAll()
    }
}