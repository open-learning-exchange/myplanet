package org.ole.planet.myplanet.utils

import androidx.annotation.VisibleForTesting
import com.google.gson.JsonObject
import java.util.Collections
import java.util.Locale
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.utils.JsonUtils.getStringAsJsonArray

object ExamAnswerUtils {
    // Process-lifetime cache mapping a stringified choices JSON to a Map of id -> text.
    // Using choices as the key prevents stale mapping if the question's choices are updated from the server.
    private val choicesCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Map<String, String>>(134, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>): Boolean {
                return size > 100
            }
        }
    )

    @VisibleForTesting
    internal fun cacheSize(): Int = choicesCache.size

    fun choiceDisplayValue(choice: JsonObject): String? {
        return JsonUtils.getString("text", choice).ifBlank {
            JsonUtils.getString("res", choice).ifBlank { null }
        }
    }

    fun getChoiceTextById(question: ExamQuestion, id: String): String {
        val choicesString = question.choices ?: return id

        var map = choicesCache.get(choicesString)
        if (map == null) {
            val mutableMap = mutableMapOf<String, String>()
            val choices = getStringAsJsonArray(choicesString)
            for (i in 0 until choices.size()) {
                if (choices[i].isJsonObject) {
                    val obj = choices[i].asJsonObject
                    val choiceId = JsonUtils.getString("id", obj)
                    val displayValue = choiceDisplayValue(obj)
                    if (choiceId.isNotEmpty() && displayValue != null) {
                        mutableMap[choiceId] = displayValue
                    }
                }
            }
            map = mutableMap
            choicesCache.put(choicesString, map)
        }

        return map[id] ?: id
    }

    fun checkCorrectAnswer(
        ans: String,
        listAns: Map<String, String>?,
        question: ExamQuestion?
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
        if (correctChoices == null) return false
        val locale = Locale.getDefault()
        val normalizedAns = ans.lowercase(locale)
        return correctChoices.any { it.lowercase(locale) == normalizedAns }
    }

    private fun checkMultipleSelectAnswer(
        listAns: Map<String, String>?,
        correctChoices: List<String>?
    ): Boolean {
        if (listAns == null || correctChoices == null) return false
        val locale = Locale.getDefault()
        val selectedAns = listAns.values.map { it.lowercase(locale) }.sorted()
        val correctList = correctChoices.map { it.lowercase(locale) }.sorted()
        return selectedAns == correctList
    }

    private fun checkTextAnswer(ans: String, correctChoices: List<String>?): Boolean {
        if (correctChoices == null) return false
        val locale = Locale.getDefault()
        val normalizedAns = ans.lowercase(locale)
        return correctChoices.any {
            normalizedAns.contains(it.lowercase(locale))
        }
    }
}
