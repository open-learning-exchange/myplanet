package org.ole.planet.myplanet.ui.exam

import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.GsonUtils

/** A question's choice: an object choice's `text` and `id`, or a plain choice's label with no [id]. */
internal data class ExamChoice(val text: String, val id: String?)

/** Reads a question's `choices` text, and the choices a saved answer holds, as [ExamTakingFragment] shows them. */
internal object ExamChoices {
    val OTHER = ExamChoice("Other", "other")

    /** Throws, as the question screen always has, on choices text that is not valid JSON. */
    fun parse(choices: String?): List<ExamChoice> {
        val array = GsonUtils.getStringAsJsonArray(choices)
        return (0 until array.size()).map { i ->
            val element = array[i]
            if (element.isJsonObject) {
                val choice = element.asJsonObject
                ExamChoice(GsonUtils.getString("text", choice), GsonUtils.getString("id", choice))
            } else {
                ExamChoice(GsonUtils.getString(array, i), null)
            }
        }
    }

    /** A saved answer's choice; throws on text that is not a JSON object. */
    fun saved(choice: String): ExamChoice {
        val choiceJson = GsonUtils.gson.fromJson(choice, JsonObject::class.java)
        return ExamChoice(GsonUtils.getString("text", choiceJson), GsonUtils.getString("id", choiceJson))
    }
}
