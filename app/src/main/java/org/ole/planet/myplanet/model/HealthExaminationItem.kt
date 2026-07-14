package org.ole.planet.myplanet.model

import com.google.gson.JsonObject

data class HealthExaminationItem(
    val _id: String,
    val temperature: Float,
    val pulse: Int,
    val bp: String,
    val height: Float,
    val weight: Float,
    val vision: String,
    val hearing: String,
    val date: Long,
    val encryptedData: JsonObject?,
    val conditions: String,
    val createdBy: String,
    val createdByName: String?
)
