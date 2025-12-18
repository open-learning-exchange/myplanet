package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.GsonUtils
import java.util.Date
import javax.inject.Inject

class ExaminationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService, private val gson: Gson
) : RealmRepository(databaseService), ExaminationRepository {
    override suspend fun getHealthAndUser(userId: String): Pair<RealmMyHealthPojo?, RealmUserModel?> {
        return withRealm { realm ->
            var pojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
            if (pojo == null) {
                pojo = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
            }
            val user = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()

            if (user != null && (user.key == null || user.iv == null)) {
                realm.executeTransaction {
                    user.key = AndroidDecrypter.generateKey()
                    user.iv = AndroidDecrypter.generateIv()
                }
            }
            Pair(pojo?.let { realm.copyFromRealm(it) }, user?.let { realm.copyFromRealm(it) })
        }
    }

    override suspend fun getDecryptedHealth(
        pojo: RealmMyHealthPojo?, user: RealmUserModel?
    ): RealmMyHealth? {
        if (pojo != null && pojo.data?.isNotEmpty() == true) {
            return gson.fromJson(
                AndroidDecrypter.decrypt(pojo.data, user?.key, user?.iv), RealmMyHealth::class.java
            )
        }
        return RealmMyHealth().apply {
            lastExamination = Date().time
            userKey = AndroidDecrypter.generateKey()
            profile = RealmMyHealth.RealmMyHealthProfile()
        }
    }

    override suspend fun getExamination(examinationId: String): RealmMyHealthPojo? {
        return findByField(RealmMyHealthPojo::class.java, "_id", examinationId)
    }

    override suspend fun saveExamination(
        examination: RealmMyHealthPojo?,
        health: RealmMyHealth?,
        user: RealmUserModel?,
        currentUser: RealmUserModel?,
        sign: RealmExamination,
        conditions: Map<String?, Boolean>,
        temperature: Float,
        pulse: Int,
        height: Float,
        weight: Float,
        vision: String,
        hearing: String,
        bp: String,
        hasInfo: Boolean
    ): Boolean {
        return try {
            executeTransaction { realm ->
                val examToSave = if (examination?._id != null) {
                    realm.where(RealmMyHealthPojo::class.java).equalTo("_id", examination._id).findFirst()
                } else {
                    realm.createObject(
                        RealmMyHealthPojo::class.java, AndroidDecrypter.generateIv()
                    )
                }

                examToSave?.apply {
                    this.userId = user?.id
                    this.profileId = health?.userKey
                    this.creatorId = health?.userKey
                    this.gender = user?.gender
                    this.age = user?.dob?.let { org.ole.planet.myplanet.utilities.TimeUtils.getAge(it) } ?: 0
                    this.isSelfExamination = currentUser?._id == user?._id
                    this.date = Date().time
                    this.planetCode = user?.planetCode
                    this.bp = bp
                    this.setTemperature(temperature)
                    this.pulse = pulse
                    this.setWeight(weight)
                    this.height = height
                    this.conditions = gson.toJson(conditions)
                    this.hearing = hearing
                    this.vision = vision
                    this.isUpdated = true
                    this.isHasInfo = hasInfo

                    val managedUser = realm.where(RealmUserModel::class.java).equalTo("id", user?.id).findFirst()
                    val key = managedUser?.key ?: AndroidDecrypter.generateKey().also { managedUser?.key = it }
                    val iv = managedUser?.iv ?: AndroidDecrypter.generateIv().also { managedUser?.iv = it }
                    this.data = AndroidDecrypter.encrypt(gson.toJson(sign), key, iv)
                }

                var healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", user?.id).findFirst()
                if (healthPojo == null) {
                    healthPojo = realm.createObject(RealmMyHealthPojo::class.java, user?.id)
                    healthPojo.userId = user?._id
                }
                health?.lastExamination = Date().time
                val managedUser = realm.where(RealmUserModel::class.java).equalTo("id", user?.id).findFirst()
                val userKey = managedUser?.key
                val userIv = managedUser?.iv
                if (userKey != null && userIv != null) {
                    healthPojo.data = AndroidDecrypter.encrypt(gson.toJson(health), userKey, userIv)
                }
                healthPojo.isUpdated = true
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
