package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class CourseProgress(
    val courseTitle: String?,
    val progress: Int,
    val stepProgress: JsonArray,
    val currentProgress: Int,
    val maxProgress: Int
)
