package org.ole.planet.myplanet.utils

import androidx.annotation.VisibleForTesting
import java.util.Collections
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.ole.planet.myplanet.model.ExamQuestion

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
        return KJson.getString("text", choice).ifBlank {
            KJson.getString("res", choice).ifBlank { null }
        }
    }

    fun getChoiceTextById(question: ExamQuestion, id: String): String {
        val choicesString = question.choices ?: return id

        var map = choicesCache.get(choicesString)
        if (map == null) {
            val mutableMap = mutableMapOf<String, String>()
            for (obj in parseChoices(choicesString)) {
                if (obj is JsonObject) {
                    val choiceId = KJson.getString("id", obj)
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

    /** A question's choices in Gson's lenient grammar; throws on malformed text, none for any other document. */
    private fun parseChoices(choices: String): JsonArray =
        if (choices.isBlank()) JsonArray(emptyList()) else KJson.parseOrNull(choices) as? JsonArray ?: JsonArray(emptyList())

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
        if (listAns.size != correctChoices.size) return false
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
