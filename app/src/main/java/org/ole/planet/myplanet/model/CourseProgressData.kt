package org.ole.planet.myplanet.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import org.ole.planet.myplanet.utils.KJson

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
        val obj = KJson.asJsonObject(element)
        val stepId = if (obj.containsKey("stepId") && obj["stepId"] !is JsonNull) KJson.gsonAsString(obj["stepId"], KJson.GsonNumbers.LITERAL) else null
        val percentage = if (obj.containsKey("percentage")) KJson.gsonAsString(obj["percentage"], KJson.GsonNumbers.LITERAL) else null
        val completed = obj.containsKey("percentage") && KJson.gsonAsBoolean(obj["completed"])
        StepProgressCell(stepId, percentage, completed)
    }
}
