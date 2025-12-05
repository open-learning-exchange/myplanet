package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion

data class QuestionChoice(
    val id: String?,
    val text: String?
)

data class QuestionAnswerViewModel(
    val question: RealmExamQuestion,
    val answer: RealmAnswer?,
    val choices: List<QuestionChoice>,
    val answerChoiceTexts: List<String>
)

object QuestionMapper {
    fun toViewModel(question: RealmExamQuestion, answer: RealmAnswer?): QuestionAnswerViewModel {
        val questionChoices = parseChoices(question.choices)
        val answerChoiceTexts = parseAnswerChoices(answer, questionChoices)
        return QuestionAnswerViewModel(question, answer, questionChoices, answerChoiceTexts)
    }

    private fun parseAnswerChoices(answer: RealmAnswer?, questionChoices: List<QuestionChoice>): List<String> {
        if (answer == null || answer.valueChoices.isNullOrEmpty()) {
            return emptyList()
        }

        val questionChoicesById = questionChoices.associateBy { it.id }
        val selectedChoices = mutableListOf<String>()

        for (choice in answer.valueChoices!!) {
            try {
                val choiceJson = org.ole.planet.myplanet.utilities.GsonUtils.gson.fromJson(choice, com.google.gson.JsonObject::class.java)
                val choiceId = choiceJson.get("id")?.asString
                val choiceText = choiceJson.get("text")?.asString

                if (!choiceText.isNullOrEmpty()) {
                    selectedChoices.add(choiceText)
                } else if (!choiceId.isNullOrEmpty()) {
                    val matchingChoice = questionChoicesById[choiceId]
                    if (matchingChoice != null) {
                        selectedChoices.add(matchingChoice.text ?: choiceId)
                    } else {
                        selectedChoices.add(choiceId)
                    }
                }
            } catch (e: Exception) {
                selectedChoices.add(choice)
            }
        }
        return selectedChoices
    }

    private fun parseChoices(choicesJson: String?): List<QuestionChoice> {
        if (choicesJson.isNullOrEmpty()) {
            return emptyList()
        }
        return try {
            val choiceType = object : com.google.gson.reflect.TypeToken<List<QuestionChoice>>() {}.type
            org.ole.planet.myplanet.utilities.GsonUtils.gson.fromJson(choicesJson, choiceType)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
