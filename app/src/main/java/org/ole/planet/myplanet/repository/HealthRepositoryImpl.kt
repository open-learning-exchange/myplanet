package org.ole.planet.myplanet.repository

import android.text.TextUtils
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.JsonUtils
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

    override suspend fun saveHealthExamination(
        userId: String,
        examination: RealmHealthExamination,
        profile: RealmMyHealth.RealmMyHealthProfile
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                executeTransaction { realm ->
                    realm.copyToRealmOrUpdate(examination)

                    val user = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
                    if (user != null) {
                        var pojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                        if (pojo == null) {
                            pojo = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
                        }
                        if (pojo == null) {
                            pojo = realm.createObject(RealmHealthExamination::class.java, userId)
                            pojo.userId = userId
                        }

                        var health: RealmMyHealth? = null
                        try {
                            if (!TextUtils.isEmpty(pojo.data)) {
                                health = JsonUtils.gson.fromJson(
                                    AndroidDecrypter.decrypt(pojo.data, user.key, user.iv),
                                    RealmMyHealth::class.java
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (health == null) {
                            health = RealmMyHealth()
                            if (!TextUtils.isEmpty(examination.profileId)) {
                                health.userKey = examination.profileId
                            } else {
                                health.userKey = AndroidDecrypter.generateKey()
                            }
                        }

                        health.profile = profile
                        health.lastExamination = Date().time

                        try {
                            pojo.data = AndroidDecrypter.encrypt(JsonUtils.gson.toJson(health), user.key, user.iv)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}
