package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUserModel

interface HealthRepository {
    fun getHealthExaminationByUserId(userId: String): RealmHealthExamination?
    suspend fun saveHealthData(userId: String, healthProfile: RealmMyHealth.RealmMyHealthProfile, userUpdates: UserHealthUpdates)
    suspend fun getHealthDataForUser(userId: String): HealthData?
    suspend fun saveExamination(userId: String, examinationId: String?, data: ExaminationData, currentUserId: String?)
    suspend fun checkUserKeys(userId: String)
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

data class ExaminationData(
    val temperature: Float,
    val pulse: Int,
    val bp: String,
    val height: Float,
    val weight: Float,
    val vision: String,
    val hearing: String,
    val observation: String,
    val diagnosis: String,
    val treatments: String,
    val medications: String,
    val immunizations: String,
    val allergies: String,
    val xrays: String,
    val tests: String,
    val referrals: String,
    val conditions: String?,
    val hasInfo: Boolean,
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
