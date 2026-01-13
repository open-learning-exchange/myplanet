package org.ole.planet.myplanet.model

import com.google.gson.JsonArray

data class CourseProgressData(
    val title: String?,
    val current: Int,
    val max: Int,
    val steps: JsonArray
)
