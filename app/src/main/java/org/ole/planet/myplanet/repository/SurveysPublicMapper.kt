package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import javax.inject.Inject
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.valueChoicesArray

class SurveysPublicMapper @Inject constructor(
    private val surveysRepository: SurveysRepository
) {
    suspend fun buildPublicAnswers(surveyId: String, submission: Submission): JsonArray {
        val questions = surveysRepository.getExamQuestions(surveyId)
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

    fun parseRespondent(userJson: String?): JsonObject? {
        return userJson?.takeIf { it.isNotBlank() && it != "{}" }?.let {
            try {
                JsonParser.parseString(it).asJsonObject.also(::sanitizeRespondent)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun sanitizeRespondent(user: JsonObject) {
        if (user.has("age")) {
            val age = user.get("age").asString.trim().toIntOrNull()
            if (age != null) user.addProperty("age", age) else user.remove("age")
        }
    }
}
