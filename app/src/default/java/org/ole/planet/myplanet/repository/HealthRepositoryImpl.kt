package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import java.util.Date
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService, gson), HealthRepository {

    override suspend fun getHealthRecord(userId: String): RealmMyHealthPojo? {
        return withRealm { realm ->
            var record = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
            if (record == null) {
                record = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
            }
            record?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getHealthData(healthRecord: RealmMyHealthPojo?, user: RealmUserModel?): RealmMyHealth? {
        if (healthRecord == null || user == null || healthRecord.data.isNullOrEmpty()) {
            return null
        }
        return gson.fromJson(AndroidDecrypter.decrypt(healthRecord.data, user.key, user.iv), RealmMyHealth::class.java)
    }

    override suspend fun getExamination(examinationId: String): RealmMyHealthPojo? {
        return withRealm { realm ->
            val examination = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", examinationId).findFirst()
            examination?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getAllExaminations(userId: String): List<RealmMyHealthPojo> {
        return withRealm { realm ->
            val examinations = realm.where(RealmMyHealthPojo::class.java)
                .equalTo("userId", userId)
                .findAll()
            realm.copyFromRealm(examinations)
        }
    }

    override suspend fun saveExamination(examination: RealmMyHealthPojo, user: RealmUserModel?) {
        databaseService.executeTransactionAsync { realm ->
            realm.insertOrUpdate(examination)
            user?.let { realm.insertOrUpdate(it) }
        }
    }

    override suspend fun initHealthData(userId: String): RealmMyHealth {
        return withRealm {
            val health = RealmMyHealth()
            val profile = RealmMyHealth.RealmMyHealthProfile()
            health.lastExamination = Date().time
            health.userKey = generateKey()
            health.profile = profile
            health
        }
    }

    override suspend fun updateHealthRecord(healthRecord: RealmMyHealthPojo?, healthData: RealmMyHealth?, user: RealmUserModel?) {
        if (healthRecord == null || healthData == null || user == null) return
        val encryptedData = AndroidDecrypter.encrypt(gson.toJson(healthData), user.key, user.iv)
        healthRecord.data = encryptedData
        databaseService.executeTransactionAsync { realm ->
            realm.insertOrUpdate(healthRecord)
        }
    }
    override suspend fun getUser(userId: String): RealmUserModel? {
        return withRealm { realm ->
            val user = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            user?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun createOrUpdateHealthRecord(userId: String, healthData: RealmMyHealth, user: RealmUserModel?) {
        if (user == null) return
        val healthRecord = getHealthRecord(userId) ?: RealmMyHealthPojo().apply {
            this._id = userId
            this.userId = user._id
        }

        healthData.lastExamination = Date().time
        val userKey = user.key ?: generateKey().also { user.key = it }
        val userIv = user.iv ?: generateIv().also { user.iv = it }

        healthRecord.data = AndroidDecrypter.encrypt(gson.toJson(healthData), userKey, userIv)
        healthRecord.isUpdated = true

        databaseService.executeTransactionAsync { realm ->
            realm.insertOrUpdate(healthRecord)
            realm.insertOrUpdate(user)
        }
    }

    override suspend fun createExamination(
        userId: String,
        healthRecord: RealmMyHealth?,
        currentUser: RealmUserModel,
        user: RealmUserModel?
    ): RealmMyHealthPojo {
        return RealmMyHealthPojo().apply {
            this._id = generateIv()
            this.userId = userId
            this.profileId = healthRecord?.userKey
            this.creatorId = healthRecord?.userKey
            this.isSelfExamination = currentUser._id == userId
            this.date = Date().time
            this.planetCode = user?.planetCode
            this.isUpdated = true
        }
    }
}
