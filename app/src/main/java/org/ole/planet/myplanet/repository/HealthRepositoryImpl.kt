package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), HealthRepository {
    override suspend fun getHealthExaminationByUserId(userId: String): RealmHealthExamination? {
        return withRealm { realm ->
            realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getHealthExaminationById(id: String): RealmHealthExamination? {
        return withRealm { realm ->
            realm.where(RealmHealthExamination::class.java).equalTo("_id", id).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun addExamination(examination: RealmHealthExamination, healthData: JsonObject?, userId: String, user: RealmUserModel) {
        executeTransaction { r ->
            val exam = r.copyToRealmOrUpdate(examination)
            if (healthData != null) {
                try {
                    exam.data = AndroidDecrypter.encrypt(Gson().toJson(healthData), user.key, user.iv)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun updateExamination(examination: RealmHealthExamination, healthData: JsonObject?, user: RealmUserModel) {
        executeTransaction { r ->
            val exam = r.copyToRealmOrUpdate(examination)
             if (healthData != null) {
                try {
                    exam.data = AndroidDecrypter.encrypt(Gson().toJson(healthData), user.key, user.iv)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
