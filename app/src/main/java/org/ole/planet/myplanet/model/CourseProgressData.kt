package org.ole.planet.myplanet.model

import com.google.gson.JsonArray

data class StepProgressCell(
    val stepId: String?,
    val percentage: String?,
    val completed: Boolean
)

data class CourseProgressData(
    val title: String?,
    val current: Int,
    val max: Int,
    val steps: JsonArray
)

fun CourseProgressData.toStepCells(): List<StepProgressCell> {
    return steps.map { element ->
        val obj = element.asJsonObject
        val stepId = if (obj.has("stepId") && !obj["stepId"].isJsonNull) obj["stepId"].asString else null
        val percentage = if (obj.has("percentage")) obj["percentage"].asString else null
        val completed = obj.has("percentage") && obj["completed"].asBoolean
        StepProgressCell(stepId, percentage, completed)
    }
}
