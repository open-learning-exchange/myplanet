package org.ole.planet.myplanet.repository

import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : HealthRepository, RealmRepository(databaseService) {

    override suspend fun getHealthRecords(userId: String?): RealmMyHealthPojo? {
        if (userId.isNullOrEmpty()) return null
        return withRealm { realm ->
            var healthRecord = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("_id", userId)
                .findFirst()
            if (healthRecord == null) {
                healthRecord = realm.where(RealmMyHealthPojo::class.java)
                    .equalTo("userId", userId)
                    .findFirst()
            }
            healthRecord?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getExaminations(userKey: String?): List<RealmMyHealthPojo> {
        if (userKey.isNullOrEmpty()) return emptyList()
        return withRealm { realm ->
            val healths = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("profileId", userKey)
                .findAll()
            realm.copyFromRealm(healths)
        }
    }
}