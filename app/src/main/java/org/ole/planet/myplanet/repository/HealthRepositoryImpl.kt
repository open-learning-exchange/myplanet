package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utils.JsonUtils
import java.util.Date
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), HealthRepository {

    override suspend fun getHealthProfile(userId: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", userId)
    }

    override suspend fun getExamination(id: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", id)
    }

    override suspend fun getUser(userId: String): RealmUser? {
        return findByField(RealmUser::class.java, "id", userId)
    }

    override suspend fun ensureUserEncryptionKeys(userId: String) {
        executeTransaction { realm ->
            val user = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
            if (user != null && (user.key == null || user.iv == null)) {
                user.key = generateKey()
                user.iv = generateIv()
            }
        }
    }

    override suspend fun saveExamination(
        userId: String,
        examinationId: String?,
        profile: RealmMyHealth,
        details: RealmExamination,
        examinationFields: RealmHealthExamination
    ) {
        executeTransaction { realm ->
            val user = realm.where(RealmUser::class.java).equalTo("id", userId).findFirst()
            val userKey = user?.key
            val userIv = user?.iv

            if (userKey != null && userIv != null) {
                // Update Profile (pojo)
                var pojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                if (pojo == null) {
                    pojo = realm.createObject(RealmHealthExamination::class.java, userId)
                    pojo?.userId = user._id
                }

                // profile is RealmMyHealth POJO
                pojo?.data = encrypt(JsonUtils.gson.toJson(profile), userKey, userIv)
                pojo?.isUpdated = true

                // Update Examination Record
                var examination: RealmHealthExamination? = null
                if (examinationId != null) {
                    examination = realm.where(RealmHealthExamination::class.java).equalTo("_id", examinationId).findFirst()
                }

                if (examination == null) {
                    val newId = generateIv() // Using generateIv as ID generator
                    examination = realm.createObject(RealmHealthExamination::class.java, newId)
                    // The activity sets examination?.userId = odUserId (which is newId)
                    // examination?.userId = odUserId
                    examination?.userId = newId
                }

                examination?.profileId = profile.userKey
                examination?.creatorId = profile.userKey
                examination?.gender = user.gender
                examination?.age = examinationFields.age
                examination?.isSelfExamination = examinationFields.isSelfExamination
                examination?.date = Date().time
                examination?.planetCode = user.planetCode
                examination?.bp = examinationFields.bp
                examination?.setTemperature(examinationFields.temperature)
                examination?.pulse = examinationFields.pulse
                examination?.setWeight(examinationFields.weight)
                examination?.height = examinationFields.height
                examination?.conditions = examinationFields.conditions
                examination?.hearing = examinationFields.hearing
                examination?.vision = examinationFields.vision

                examination?.isUpdated = true
                examination?.isHasInfo = examinationFields.isHasInfo

                examination?.data = encrypt(JsonUtils.gson.toJson(details), userKey, userIv)
            }
        }
    }
}
