package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity

interface HealthRepository {
    suspend fun getHealthEntry(userId: String): Pair<UserEntity?, HealthExamination?>
    suspend fun getExaminationById(id: String): HealthExamination?
    suspend fun initHealth(): MyHealth
    suspend fun saveExamination(examination: HealthExamination?, pojo: HealthExamination?, user: UserEntity?)
    suspend fun getUpdatedHealthExaminations(): List<HealthExamination>
    suspend fun getUpdatedHealthForUser(userId: String): List<HealthExamination>
    suspend fun markHealthExaminationsUploaded(idToRevMap: Map<String, String?>)
    suspend fun updateExaminationUserId(id: String, userId: String)
    suspend fun bulkInsertFromSync(jsonArray: JsonArray)
    suspend fun uploadHealthData(myHealths: List<HealthExamination>): Map<String, String?>
    suspend fun getExaminationConditions(examination: HealthExamination?): Map<String, Boolean>
    suspend fun getByIdOrUserId(id: String): HealthExamination?
    suspend fun getHealthProfile(userId: String): MyHealth?
    suspend fun getDecryptedHealth(pojo: HealthExamination?, user: UserEntity?): MyHealth?
    suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>)
    suspend fun getByProfileId(profileId: String): List<HealthExamination>
    suspend fun upsert(examination: HealthExamination)
    suspend fun getPatientById(id: String): UserEntity?
    suspend fun getPatientsSortedBy(fieldName: String, descending: Boolean): List<UserEntity>
    suspend fun searchPatients(query: String, sortField: String, descending: Boolean): List<UserEntity>
    suspend fun getPatientHealthRecords(userId: String, currentUser: UserEntity): HealthRecord?
}
