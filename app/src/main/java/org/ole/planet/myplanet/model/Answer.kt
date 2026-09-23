package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

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
    @get:Ignore
    val valueChoicesArray: JsonArray
        get() {
            if (valueChoices == null) {
                return JsonArray()
            }
            return buildJsonArray {
                for (choice in valueChoices ?: emptyList()) {
                    val parsed = GsonUtils.gson.fromJson(choice, JsonObject::class.java)
                    add(parsed?.toKotlinx() ?: JsonNull)
                }
            }.toGson()
        }

    companion object {
        fun serializeAnswer(answers: List<Answer>): JsonArray = buildJsonArray {
            for (ans in answers) {
                add(createObject(ans).toKotlinx())
            }
        }.toGson()

        private fun createObject(ans: Answer): JsonObject = buildJsonObject {
            if (!ans.value.isNullOrEmpty()) {
                put("value", ans.value)
            } else {
                put("value", ans.valueChoicesArray.toKotlinx())
            }
            put("mistakes", ans.mistakes)
            put("passed", ans.isPassed)
            if (!ans.questionId.isNullOrEmpty()) {
                put("questionId", ans.questionId)
            }
        }.toGson()
    }
}
