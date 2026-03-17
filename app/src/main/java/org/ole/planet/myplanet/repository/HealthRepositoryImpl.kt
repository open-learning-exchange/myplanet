package org.ole.planet.myplanet.repository

import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), HealthRepository {
    override suspend fun getHealthEntry(userId: String): Pair<RealmUser?, RealmHealthExamination?> {
        val userCopy = findByField(RealmUser::class.java, "id", userId)
        val pojoCopy = findByField(RealmHealthExamination::class.java, "_id", userId)
            ?: findByField(RealmHealthExamination::class.java, "userId", userId)

        return Pair(userCopy, pojoCopy)
    }

    override suspend fun getExaminationById(id: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", id)
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
}
