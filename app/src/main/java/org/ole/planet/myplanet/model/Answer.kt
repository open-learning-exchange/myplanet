package org.ole.planet.myplanet.model
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Ignore
import androidx.room.PrimaryKey

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.JsonUtils

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
            val array = JsonArray()
            if (valueChoices == null) {
                return array
            }
            for (choice in valueChoices ?: emptyList()) {
                array.add(JsonUtils.gson.fromJson(choice, JsonObject::class.java))
            }
            return array
        }

    companion object {
        fun serializeRealmAnswer(answers: List<Answer>): JsonArray {
            val array = JsonArray()
            for (ans in answers) {
                array.add(createObject(ans))
            }
            return array
        }

        private fun createObject(ans: Answer): JsonObject {
            val `object` = JsonObject()
            if (!TextUtils.isEmpty(ans.value)) {
                `object`.addProperty("value", ans.value)
            } else {
                `object`.add("value", ans.valueChoicesArray)
            }
            `object`.addProperty("mistakes", ans.mistakes)
            `object`.addProperty("passed", ans.isPassed)
            if (!TextUtils.isEmpty(ans.questionId)) {
                `object`.addProperty("questionId", ans.questionId)
            }
            return `object`
        }
    }
}
