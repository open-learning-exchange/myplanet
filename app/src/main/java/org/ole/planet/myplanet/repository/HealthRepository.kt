package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth

interface HealthRepository {
    fun getHealthExaminationByUserId(userId: String): RealmHealthExamination?
    suspend fun saveHealthData(userId: String, healthProfile: RealmMyHealth.RealmMyHealthProfile, userUpdates: UserHealthUpdates)
    suspend fun getHealthDataForUser(userId: String): HealthData?
}

data class HealthData(
    val myHealth: RealmMyHealth?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val dob: String?,
    val birthPlace: String?
)

data class UserHealthUpdates(
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val email: String,
    val dob: String,
    val birthPlace: String,
    val phoneNumber: String
)
