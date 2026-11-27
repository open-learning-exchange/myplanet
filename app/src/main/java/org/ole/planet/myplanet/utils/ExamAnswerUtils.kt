package org.ole.planet.myplanet.utils

import androidx.annotation.VisibleForTesting
import com.google.gson.JsonObject
import java.util.Collections
import java.util.Locale
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.utils.GsonUtils.getStringAsJsonArray

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
        return GsonUtils.getString("text", choice).ifBlank {
            GsonUtils.getString("res", choice).ifBlank { null }
        }
    }

    fun getChoiceTextById(question: ExamQuestion, id: String): String {
        val choicesString = question.choices ?: return id

        var map = choicesCache[choicesString]
        if (map == null) {
            val mutableMap = mutableMapOf<String, String>()
            val choices = getStringAsJsonArray(choicesString)
            for (i in 0 until choices.size()) {
                if (choices[i].isJsonObject) {
                    val obj = choices[i].asJsonObject
                    val choiceId = GsonUtils.getString("id", obj)
                    val displayValue = choiceDisplayValue(obj)
                    if (choiceId.isNotEmpty() && displayValue != null) {
                        mutableMap[choiceId] = displayValue
                    }
                }
            }
            map = mutableMap
            choicesCache[choicesString] = map
        }

        return map[id] ?: id
    }

    private val OTHER_FOLLOW_UP = Regex("""^\W*if\s+others?\b""", RegexOption.IGNORE_CASE)
    private val OTHER_CHOICE = Regex("""^\W*others?\b""", RegexOption.IGNORE_CASE)

    fun isOtherFollowUp(question: ExamQuestion, previous: ExamQuestion?): Boolean {
        if (previous == null) return false
        val isTextQuestion = question.type.equals("input", ignoreCase = true) ||
            question.type.equals("textarea", ignoreCase = true)
        if (!isTextQuestion || !OTHER_FOLLOW_UP.containsMatchIn(question.body.orEmpty())) return false
        return previous.hasOtherOption || otherChoices(previous).isNotEmpty()
    }
    
    fun isOtherChoiceSelected(
        question: ExamQuestion,
        singleAnswer: String,
        multipleAnswers: Map<String, String>
    ): Boolean {
        val others = otherChoices(question).toMutableList()
        if (question.hasOtherOption) others.add("other" to "Other")
        return others.any { (id, text) ->
            singleAnswer.equals(id, ignoreCase = true) || singleAnswer.equals(text, ignoreCase = true) ||
                multipleAnswers.keys.any { it.equals(text, ignoreCase = true) } ||
                multipleAnswers.values.any { it.equals(id, ignoreCase = true) }
        }
    }

    private fun otherChoices(question: ExamQuestion): List<Pair<String, String>> {
        val choicesString = question.choices ?: return emptyList()
        val choices = getStringAsJsonArray(choicesString)
        return (0 until choices.size()).mapNotNull { i ->
            val obj = choices[i].takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val text = choiceDisplayValue(obj) ?: return@mapNotNull null
            if (OTHER_CHOICE.containsMatchIn(text)) GsonUtils.getString("id", obj) to text else null
        }
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
