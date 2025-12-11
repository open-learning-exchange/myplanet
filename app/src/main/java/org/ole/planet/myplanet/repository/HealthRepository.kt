package org.ole.planet.myplanet.repository

import io.realm.Realm
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel

interface HealthRepository {
    suspend fun getHealthRecord(userId: String): RealmMyHealthPojo?
    suspend fun getHealthData(healthRecord: RealmMyHealthPojo?, user: RealmUserModel?): RealmMyHealth?
    suspend fun getExamination(examinationId: String): RealmMyHealthPojo?
    suspend fun getAllExaminations(userId: String): List<RealmMyHealthPojo>
    suspend fun saveExamination(examination: RealmMyHealthPojo, user: RealmUserModel?)
    suspend fun initHealthData(userId: String) : RealmMyHealth
    suspend fun updateHealthRecord(healthRecord: RealmMyHealthPojo?, healthData: RealmMyHealth?, user: RealmUserModel?)
    suspend fun getUser(userId: String): RealmUserModel?
    suspend fun createOrUpdateHealthRecord(userId: String, healthData: RealmMyHealth, user: RealmUserModel?)
    suspend fun createExamination(userId: String, healthRecord: RealmMyHealth?, currentUser: RealmUserModel, user: RealmUserModel?): RealmMyHealthPojo
}
