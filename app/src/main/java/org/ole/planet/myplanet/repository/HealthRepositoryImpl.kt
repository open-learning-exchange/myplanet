package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter
import java.util.Date

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

    override suspend fun initHealth(): RealmMyHealth {
        return withContext(Dispatchers.Default) {
            val health = RealmMyHealth()
            val profile = RealmMyHealth.RealmMyHealthProfile()
            health.lastExamination = Date().time
            health.userKey = AndroidDecrypter.generateKey()
            health.profile = profile
            health
        }
    }

    override suspend fun saveExamination(examination: RealmHealthExamination?, pojo: RealmHealthExamination?, user: RealmUser?) {
        databaseService.executeTransactionAsync { realm ->
            user?.let { realm.copyToRealmOrUpdate(it) }
            pojo?.let { realm.copyToRealmOrUpdate(it) }
            examination?.let { realm.copyToRealmOrUpdate(it) }
        }
    }

    override suspend fun getUpdatedHealthExaminations(userId: String?): List<RealmHealthExamination> {
        return withRealm { realm ->
            val query = realm.where(RealmHealthExamination::class.java).equalTo("isUpdated", true)
            if (userId.isNullOrEmpty()) {
                query.notEqualTo("userId", "")
            } else {
                query.equalTo("userId", userId)
            }
            query.findAll().map { realm.copyFromRealm(it) }
        }
    }

    override suspend fun markHealthUploaded(examinationId: String, rev: String) {
        databaseService.executeTransactionAsync { realm ->
            val managedPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", examinationId).findFirst()
            managedPojo?._rev = rev
            managedPojo?.isUpdated = false
        }
    }
}
