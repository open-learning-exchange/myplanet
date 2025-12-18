package org.ole.planet.myplanet.repository

import android.text.TextUtils
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.myhealth.HealthData
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.GsonUtils
import javax.inject.Inject

class MyHealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), MyHealthRepository {

    override suspend fun getHealthData(userId: String?): HealthData {
        return withRealm { realm ->
            val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            val healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
                ?: realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()

            var decodedHealth: RealmMyHealth? = null
            if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
                try {
                    decodedHealth = GsonUtils.gson.fromJson(
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

    override suspend fun saveHealthData(userId: String?, health: RealmMyHealth, healthData: HealthData) {
        executeTransaction { realm ->
            val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            userModel?.firstName = healthData.firstName
            userModel?.middleName = healthData.middleName
            userModel?.lastName = healthData.lastName
            userModel?.email = healthData.email
            userModel?.dob = healthData.dob
            userModel?.birthPlace = healthData.birthPlace
            userModel?.phoneNumber = healthData.phoneNumber

            if (TextUtils.isEmpty(health.userKey)) {
                health.userKey = AndroidDecrypter.generateKey()
            }
            var healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
            if (healthPojo == null) {
                healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
            }
            if (healthPojo == null) {
                healthPojo = realm.createObject(RealmMyHealthPojo::class.java, userId)
            }
            healthPojo.isUpdated = true
            healthPojo.userId = userModel?._id
            try {
                val key = userModel?.key ?: AndroidDecrypter.generateKey().also { newKey -> userModel?.key = newKey }
                val iv = userModel?.iv ?: AndroidDecrypter.generateIv().also { newIv -> userModel?.iv = newIv }
                healthPojo.data = AndroidDecrypter.encrypt(GsonUtils.gson.toJson(health), key, iv)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
