package org.ole.planet.myplanet.ui.surveys

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Submission

@Singleton
class PublicSurveyPayloadBuilder @Inject constructor() {

    fun sanitizeRespondent(user: JsonObject): JsonObject {
        if (user.has("age")) {
            val ageElement = user.get("age")
            if (ageElement != null && ageElement.isJsonPrimitive) {
                val age = ageElement.asString.trim().toIntOrNull()
                if (age != null) {
                    user.addProperty("age", age)
                } else {
                    user.remove("age")
                }
            } else {
                user.remove("age")
            }
        }
        return user
    }

    fun buildPublicAnswers(questions: List<ExamQuestion>, submission: Submission): JsonArray {
        val answersByQuestion = submission.answers?.associateBy { it.questionId }.orEmpty()
        val payload = JsonArray()
        questions.forEach { question ->
            val answer = answersByQuestion[question.id]
            val choices = answer?.valueChoicesArray ?: JsonArray()
            when {
                question.type.equals("selectMultiple", ignoreCase = true) -> payload.add(choices)
                question.type.equals("select", ignoreCase = true) && !choices.isEmpty() -> payload.add(choices[0])
                else -> payload.add(JsonPrimitive(answer?.value.orEmpty()))
            }
        }
        return payload
    }
}
