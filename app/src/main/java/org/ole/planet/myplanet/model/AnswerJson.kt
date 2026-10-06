package org.ole.planet.myplanet.model

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

// App-side Gson helpers for Answer, kept out of the Room entity.

val Answer.valueChoicesArray: JsonArray
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

fun Answer.Companion.serializeAnswer(answers: List<Answer>): JsonArray = buildJsonArray {
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
