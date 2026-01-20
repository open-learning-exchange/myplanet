package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUserModel

interface HealthRepository {
    suspend fun getHealthExaminationByUserId(userId: String): RealmHealthExamination?
    suspend fun getHealthExaminationById(id: String): RealmHealthExamination?
    suspend fun addExamination(examination: RealmHealthExamination, healthData: JsonObject?, user: RealmUserModel)
    suspend fun updateExamination(examination: RealmHealthExamination, healthData: JsonObject?, user: RealmUserModel)
}
