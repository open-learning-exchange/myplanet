package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), HealthRepository {
    override suspend fun getHealthEntry(userId: String): Pair<RealmUser?, RealmHealthExamination?> {
        return withRealm { realm ->
            val user = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
            val userCopy = if (user != null) realm.copyFromRealm(user) else null

            var pojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
            if (pojo == null) {
                pojo = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
            }
            val pojoCopy = if (pojo != null) realm.copyFromRealm(pojo) else null

            Pair(userCopy, pojoCopy)
        }
    }

    override suspend fun getExaminationById(id: String): RealmHealthExamination? {
        return withRealm { realm ->
            val exam = realm.where(RealmHealthExamination::class.java).equalTo("_id", id).findFirst()
            if (exam != null) realm.copyFromRealm(exam) else null
        }
    }

    override suspend fun updateHealthUserId(localUserId: String, serverUserId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val list = realm.where(RealmHealthExamination::class.java).equalTo("_id", localUserId).findAll()
                list.forEach { p ->
                    p.userId = serverUserId
                }
            }
        }
    }

    override suspend fun updateHealthRev(id: String, rev: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val managedPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", id).findFirst()
                managedPojo?._rev = rev
                managedPojo?.isUpdated = false
            }
        }
    }
}
