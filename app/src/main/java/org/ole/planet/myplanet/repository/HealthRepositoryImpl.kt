package org.ole.planet.myplanet.repository

import android.text.TextUtils
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.JsonUtils

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val userRepository: UserRepository
) : RealmRepository(databaseService), HealthRepository {

    override suspend fun getHealthExamination(userId: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", userId)
            ?: findByField(RealmHealthExamination::class.java, "userId", userId)
    }

    override suspend fun saveExamination(examination: RealmHealthExamination) {
        try {
            save(examination)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getUserHealthProfile(userId: String): RealmMyHealth? {
        val user = userRepository.getUserById(userId) ?: return null
        val healthPojo = getHealthExamination(userId) ?: return null

        if (!TextUtils.isEmpty(healthPojo.data)) {
            try {
                val decrypted = AndroidDecrypter.decrypt(healthPojo.data, user.key, user.iv)
                return JsonUtils.gson.fromJson(decrypted, RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    override suspend fun getExaminationById(id: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", id)
    }

    override suspend fun ensureUserKeys(userId: String): RealmUser? {
        var user = userRepository.getUserById(userId)
        if (user != null && (user.key == null || user.iv == null)) {
            executeTransaction { realm ->
                val realmUser = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
                if (realmUser != null) {
                    if (realmUser.key == null) realmUser.key = AndroidDecrypter.generateKey()
                    if (realmUser.iv == null) realmUser.iv = AndroidDecrypter.generateIv()
                }
            }
            user = userRepository.getUserById(userId)
        }
        return user
    }
}
