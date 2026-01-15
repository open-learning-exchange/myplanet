package org.ole.planet.myplanet.repository

import android.text.TextUtils
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getAge
import java.util.Date
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(private val databaseService: DatabaseService) : HealthRepository {
    override fun getHealthExaminationByUserId(userId: String): RealmHealthExamination? {
        return getHealthExaminationByUserId(userId, databaseService.realmInstance)
    }

    private fun getHealthExaminationByUserId(userId: String, realm: Realm): RealmHealthExamination? {
        var healthPojo: RealmHealthExamination? = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
        if (healthPojo == null) {
            healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
        }
        return healthPojo
    }

    override suspend fun saveHealthData(userId: String, healthProfile: RealmMyHealth.RealmMyHealthProfile, userUpdates: UserHealthUpdates) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm: Realm ->
                val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                val myHealth = getMyHealth(realm, userId)
                val oldProfile = myHealth?.profile
                userModel?.firstName = userUpdates.firstName
                userModel?.middleName = userUpdates.middleName
                userModel?.lastName = userUpdates.lastName
                userModel?.email = userUpdates.email
                userModel?.dob = TimeUtils.convertDDMMYYYYToISO(userUpdates.dob)
                userModel?.birthPlace = userUpdates.birthPlace
                userModel?.phoneNumber = userUpdates.phoneNumber
                val emergencyContact = healthProfile.emergencyContact
                healthProfile.emergencyContact = if (TextUtils.isEmpty(emergencyContact)) {
                    oldProfile?.emergencyContact ?: ""
                } else {
                    emergencyContact
                }
                val emergencyContactType = healthProfile.emergencyContactType
                healthProfile.emergencyContactType = if (TextUtils.isEmpty(emergencyContactType)) {
                    oldProfile?.emergencyContactType ?: ""
                } else {
                    emergencyContactType
                }
                if (myHealth != null) {
                    if (TextUtils.isEmpty(myHealth.userKey)) {
                        myHealth.userKey = AndroidDecrypter.generateKey()
                    }
                    myHealth.profile = healthProfile
                    var healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                    if (healthPojo == null) {
                        healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
                    }
                    if (healthPojo == null) {
                        healthPojo = realm.createObject(RealmHealthExamination::class.java, userId)
                    }
                    healthPojo.isUpdated = true
                    healthPojo.userId = userModel?._id
                    try {
                        val key = userModel?.key ?: AndroidDecrypter.generateKey().also { newKey -> userModel?.key = newKey }
                        val iv = userModel?.iv ?: AndroidDecrypter.generateIv().also { newIv -> userModel?.iv = newIv }
                        healthPojo.data = AndroidDecrypter.encrypt(JsonUtils.gson.toJson(myHealth), key, iv)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getMyHealth(realm: Realm, userId: String): RealmMyHealth? {
        val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        val healthPojo = getHealthExaminationByUserId(userId, realm)
        var myHealth: RealmMyHealth? = null
        if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
            try {
                myHealth = JsonUtils.gson.fromJson(
                    AndroidDecrypter.decrypt(healthPojo.data, userModel?.key, userModel?.iv),
                    RealmMyHealth::class.java
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (myHealth == null) {
            myHealth = RealmMyHealth()
            myHealth.userKey = AndroidDecrypter.generateKey()
        }
        return myHealth
    }

    override suspend fun getHealthDataForUser(userId: String): HealthData? {
        return databaseService.withRealmAsync { realm ->
            val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            val healthPojo = getHealthExaminationByUserId(userId, realm)
            var decodedHealth: RealmMyHealth? = null
            if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
                try {
                    decodedHealth = JsonUtils.gson.fromJson(
                        AndroidDecrypter.decrypt(healthPojo.data, userModel?.key, userModel?.iv),
                        RealmMyHealth::class.java
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            HealthData(
                decodedHealth,
                userModel?.firstName,
                userModel?.middleName,
                userModel?.lastName,
                userModel?.email,
                userModel?.phoneNumber,
                userModel?.dob,
                userModel?.birthPlace
            )
        }
    }

    override suspend fun saveExamination(userId: String, examinationId: String?, data: ExaminationData, currentUserId: String?) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val user = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                val currentUser = realm.where(RealmUserModel::class.java).equalTo("_id", currentUserId).findFirst()
                var pojo = getHealthExaminationByUserId(userId, realm)
                if (pojo == null) {
                    pojo = realm.createObject(RealmHealthExamination::class.java, userId)
                    pojo.userId = user?._id
                }
                val myHealth = getMyHealth(realm, userId)
                myHealth?.lastExamination = Date().time
                if (user?.key != null && user.iv != null) {
                    pojo.data = AndroidDecrypter.encrypt(JsonUtils.gson.toJson(myHealth), user.key, user.iv)
                }

                var examination = if (examinationId != null) {
                    realm.where(RealmHealthExamination::class.java).equalTo("_id", examinationId).findFirst()
                } else {
                    null
                }

                if (examination == null) {
                    val newId = AndroidDecrypter.generateIv()
                    examination = realm.createObject(RealmHealthExamination::class.java, newId)
                    examination.userId = newId
                }

                examination.profileId = myHealth?.userKey
                examination.creatorId = myHealth?.userKey
                examination.gender = user?.gender
                examination.age = user?.dob?.let { getAge(it) } ?: 0
                examination.isSelfExamination = currentUser?._id == pojo?._id
                examination.date = Date().time
                examination.planetCode = user?.planetCode

                val sign = RealmExamination()
                sign.allergies = data.allergies
                sign.createdBy = currentUser?._id
                examination.bp = data.bp
                examination.setTemperature(data.temperature)
                examination.pulse = data.pulse
                examination.setWeight(data.weight)
                examination.height = data.height
                examination.conditions = data.conditions
                examination.hearing = data.hearing
                sign.immunizations = data.immunizations
                sign.tests = data.tests
                sign.xrays = data.xrays
                examination.vision = data.vision
                sign.treatments = data.treatments
                sign.referrals = data.referrals
                sign.notes = data.observation
                sign.diagnosis = data.diagnosis
                sign.medications = data.medications
                examination.date = Date().time
                examination.isUpdated = true
                examination.isHasInfo = data.hasInfo
                pojo?.isUpdated = true

                try {
                    val key = user?.key ?: AndroidDecrypter.generateKey().also { user?.key = it }
                    val iv = user?.iv ?: AndroidDecrypter.generateIv().also { user?.iv = it }
                    examination.data = AndroidDecrypter.encrypt(JsonUtils.gson.toJson(sign), key, iv)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun checkUserKeys(userId: String) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.executeTransaction { realm ->
                val user = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                if (user != null && (user.key == null || user.iv == null)) {
                    user.key = AndroidDecrypter.generateKey()
                    user.iv = AndroidDecrypter.generateIv()
                }
            }
        }
    }
}
