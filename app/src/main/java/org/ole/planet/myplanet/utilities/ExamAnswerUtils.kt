package org.ole.planet.myplanet.utilities

import java.util.Arrays
import java.util.Locale
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.utilities.JsonUtils.getStringAsJsonArray

object ExamAnswerUtils {
    fun getChoiceTextById(question: RealmExamQuestion, id: String): String {
        val choices = getStringAsJsonArray(question.choices)
        for (i in 0 until choices.size()) {
            if (choices[i].isJsonObject) {
                val obj = choices[i].asJsonObject
                if (obj.get("id").asString == id) {
                    return obj.get("text").asString
                }
            }
        }
        return id
    }

    fun checkCorrectAnswer(
        ans: String,
        listAns: Map<String, String>?,
        question: RealmExamQuestion?
    ): Boolean {
        val questionType = question?.type
        val correctChoices = question?.getCorrectChoice()
        return when {
            questionType.equals("select", ignoreCase = true) ->
                checkSelectAnswer(ans, correctChoices)
            questionType.equals("selectMultiple", ignoreCase = true) ->
                checkMultipleSelectAnswer(listAns, correctChoices)
            else -> checkTextAnswer(ans, correctChoices)
        }
    }

    private fun checkSelectAnswer(ans: String, correctChoices: List<String>?): Boolean {
        return correctChoices?.contains(ans.lowercase(Locale.getDefault())) == true
    }

    private fun checkMultipleSelectAnswer(
        listAns: Map<String, String>?,
        correctChoices: List<String>?
    ): Boolean {
        val selectedAns = listAns?.values?.toTypedArray()
        val correctChoicesArray = correctChoices?.toTypedArray()
        return isEqual(selectedAns, correctChoicesArray)
    }

    private fun checkTextAnswer(ans: String, correctChoices: List<String>?): Boolean {
        return correctChoices?.any {
            ans.lowercase(Locale.getDefault()).contains(it.lowercase(Locale.getDefault()))
        } == true
    }

    private fun isEqual(ar1: Array<String>?, ar2: Array<String>?): Boolean {
        ar1?.let { Arrays.sort(it) }
        ar2?.let { Arrays.sort(it) }
        return ar1.contentEquals(ar2)
    }
}
