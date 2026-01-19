package org.ole.planet.myplanet.repository

import io.realm.Realm
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), HealthRepository {

    override suspend fun getExaminationById(id: String): RealmHealthExamination? {
        return withRealm { realm ->
            realm.where(RealmHealthExamination::class.java).equalTo("_id", id).findFirst()?.let {
                realm.copyFromRealm(it)
            }
        }
    }

    override suspend fun getExaminationByUserId(userId: String): RealmHealthExamination? {
        return withRealm { realm ->
            realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()?.let {
                realm.copyFromRealm(it)
            }
        }
    }

    override suspend fun getUserById(id: String): RealmUserModel? {
        return withRealm { realm ->
            var user = realm.where(RealmUserModel::class.java).equalTo("id", id).findFirst()
            if (user != null && (user.key == null || user.iv == null)) {
                realm.executeTransaction {
                    user.key = AndroidDecrypter.generateKey()
                    user.iv = AndroidDecrypter.generateIv()
                }
            }
            user?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun saveExamination(examination: RealmHealthExamination, healthRecord: RealmHealthExamination) {
        executeTransaction { realm ->
            realm.copyToRealmOrUpdate(examination)
            realm.copyToRealmOrUpdate(healthRecord)
        }
    }
}
