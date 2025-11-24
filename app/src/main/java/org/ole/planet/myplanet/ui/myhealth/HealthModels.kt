package org.ole.planet.myplanet.ui.myhealth

import com.google.gson.JsonObject

data class HealthProfile(
    val profileId: String?,
    val notes: String?,
    val specialNeeds: String?,
    val birthPlace: String?,
    val emergencyContactName: String?,
    val emergencyContactType: String?,
    val emergencyContact: String?,
    val examinations: List<HealthExaminationItem>
)

data class HealthExaminationItem(
    val _id: String?,
    val temperature: Float,
    val date: Long,
    val pulse: Int,
    val bp: String?,
    val hearing: String?,
    val height: Float,
    val weight: Float,
    val vision: String?,
    val encryptedData: JsonObject?,
    val createdBy: String?,
    val createdByName: String?,
    val isSelfExamination: Boolean,
    val conditions: JsonObject?
)
