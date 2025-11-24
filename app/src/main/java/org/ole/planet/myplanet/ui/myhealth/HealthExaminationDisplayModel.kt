package org.ole.planet.myplanet.ui.myhealth

import com.google.gson.JsonObject

data class HealthExaminationDisplayModel(
    val id: String?,
    val profileId: String?,
    val temperature: String,
    val pulse: String,
    val bp: String?,
    val hearing: String?,
    val height: String,
    val weight: String,
    val vision: String?,
    val displayDate: String,
    val dateLong: Long,
    val rowColor: Int,
    val encryptedData: JsonObject?,
    val conditionsDisplay: String
)
