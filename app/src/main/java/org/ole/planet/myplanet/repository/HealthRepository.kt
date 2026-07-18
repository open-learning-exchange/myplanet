package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

interface HealthRepository {
    suspend fun getHealthEntry(userId: String): Pair<RealmUser?, HealthExamination?>
    suspend fun getExaminationById(id: String): HealthExamination?
    suspend fun initHealth(): RealmMyHealth
    suspend fun saveExamination(examination: HealthExamination?, pojo: HealthExamination?, user: RealmUser?)
    suspend fun getUpdatedHealthExaminations(): List<HealthExamination>
    suspend fun getUpdatedHealthForUser(userId: String): List<HealthExamination>
    suspend fun markHealthExaminationsUploaded(idToRevMap: Map<String, String?>)
    suspend fun updateExaminationUserId(id: String, userId: String)
    suspend fun bulkInsertFromSync(jsonArray: JsonArray)
    suspend fun uploadHealthData(myHealths: List<HealthExamination>): Map<String, String?>
    suspend fun getExaminationConditions(examination: HealthExamination?): Map<String, Boolean>
}
