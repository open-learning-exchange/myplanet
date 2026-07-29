package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.HealthExamination
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
}
