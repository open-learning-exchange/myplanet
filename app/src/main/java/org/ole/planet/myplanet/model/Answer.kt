package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.KJson

@Entity(tableName = "answers", indices = [Index("examId"), Index("questionId"), Index("submissionId")])
open class Answer(
    @PrimaryKey @JvmField var id: String = "",
    var value: String? = null,
    var valueChoices: List<String>? = null,
    var mistakes: Int = 0,
    var isPassed: Boolean = false,
    var grade: Int = 0,
    var examId: String? = null,
    var questionId: String? = null,
    var submissionId: String? = null
) {
    /** Each stored choice parsed as an object (a blank one as `null`); a stored `null` or non-object throws, as Gson did. */
    @get:Ignore
    val valueChoicesArray: JsonArray
        get() = JsonArray(valueChoices.orEmpty().map { choice -> KJson.parseObjectOrNull(choice) ?: JsonNull })

    companion object {
        fun serializeAnswer(answers: List<Answer>): JsonArray = JsonArray(answers.map { createObject(it) })

        private fun createObject(ans: Answer): JsonObject = buildJsonObject {
            if (!ans.value.isNullOrEmpty()) {
                put("value", ans.value)
            } else {
                put("value", ans.valueChoicesArray)
            }
            put("mistakes", ans.mistakes)
            put("passed", ans.isPassed)
            if (!ans.questionId.isNullOrEmpty()) {
                put("questionId", ans.questionId)
            }
        }
    }
}
