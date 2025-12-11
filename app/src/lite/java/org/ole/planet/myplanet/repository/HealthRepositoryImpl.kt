package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val gson: Gson
) : HealthRepository {
    override suspend fun getHealthRecord(userId: String): RealmMyHealthPojo? {
        return null
    }

    override suspend fun getHealthData(healthRecord: RealmMyHealthPojo?, user: RealmUserModel?): RealmMyHealth? {
        return null
    }

    override suspend fun getExamination(examinationId: String): RealmMyHealthPojo? {
        return null
    }

    override suspend fun getAllExaminations(userId: String): List<RealmMyHealthPojo> {
        return emptyList()
    }

    override suspend fun saveExamination(examination: RealmMyHealthPojo, user: RealmUserModel?) {
        // No-op in lite version
    }

    override suspend fun initHealthData(userId: String): RealmMyHealth {
        return RealmMyHealth()
    }

    override suspend fun updateHealthRecord(healthRecord: RealmMyHealthPojo?, healthData: RealmMyHealth?, user: RealmUserModel?) {
        // No-op in lite version
    }

    override suspend fun getUser(userId: String): RealmUserModel? {
        return null
    }

    override suspend fun createOrUpdateHealthRecord(userId: String, healthData: RealmMyHealth, user: RealmUserModel?) {
        // No-op in lite version
    }

    override suspend fun createExamination(userId: String, healthRecord: RealmMyHealth?, currentUser: RealmUserModel, user: RealmUserModel?): RealmMyHealthPojo {
        return RealmMyHealthPojo()
    }
}
