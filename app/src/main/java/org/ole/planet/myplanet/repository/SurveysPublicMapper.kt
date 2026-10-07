package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers

class SurveysPublicMapper @Inject constructor(
    private val surveysRepository: SurveysRepository
) {
    suspend fun buildPublicAnswers(surveyId: String, submission: Submission): JsonArray {
        val questions = surveysRepository.getExamQuestions(surveyId)
        val answersByQuestion = submission.answers?.associateBy { it.questionId }.orEmpty()
        val payload = mutableListOf<JsonElement>()
        questions.forEach { question ->
            val answer = answersByQuestion[question.id]
            val choices = answer?.valueChoicesArray?.withGsonBridgedNumbers() ?: JsonArray(emptyList())
            when {
                question.type.equals("selectMultiple", ignoreCase = true) -> payload.add(choices)
                question.type.equals("select", ignoreCase = true) && choices.isNotEmpty() -> payload.add(choices[0])
                else -> payload.add(JsonPrimitive(answer?.value.orEmpty()))
            }
        }
        return JsonArray(payload)
    }

    fun parseRespondent(userJson: String?): JsonObject? {
        return userJson?.takeIf { it.isNotBlank() && it != "{}" }?.let {
            try {
                sanitizeRespondent(KJson.asJsonObject(KJson.parseOrNull(it) ?: JsonNull))
            } catch (e: Exception) {
                null
            }
        }
    }

    /** The respondent with `age` as a number, or without it when it is not one (same key order). */
    fun sanitizeRespondent(user: JsonObject): JsonObject {
        if (!user.containsKey("age")) return user
        val age = KJson.gsonAsString(user["age"], KJson.GsonNumbers.LITERAL).trim().toIntOrNull()
        return if (age != null) JsonObject(user + ("age" to JsonPrimitive(age))) else JsonObject(user - "age")
    }
}
