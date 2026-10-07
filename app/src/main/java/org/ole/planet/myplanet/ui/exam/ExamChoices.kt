package org.ole.planet.myplanet.ui.exam

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ole.planet.myplanet.utils.KJson

/** A question's choice: an object choice's `text` and `id`, or a plain choice's label with no [id]. */
internal data class ExamChoice(val text: String, val id: String?)

/** Reads a question's `choices` text, and the choices a saved answer holds, as [ExamTakingFragment] shows them. */
internal object ExamChoices {
    val OTHER = ExamChoice("Other", "other")

    /**
     * Throws, as the question screen always has, on choices text that is not valid JSON in
     * Gson's lenient grammar; text that is valid but not an array has no choices.
     */
    fun parse(choices: String?): List<ExamChoice> {
        if (choices.isNullOrBlank()) return emptyList()
        val array = KJson.parseOrNull(choices) as? JsonArray ?: return emptyList()
        return array.map { element ->
            if (element is JsonObject) {
                ExamChoice(KJson.getString("text", element), KJson.getString("id", element))
            } else {
                ExamChoice(label(element), null)
            }
        }
    }

    /** A plain choice's label: a primitive's text (numbers as written), anything else `""`. */
    private fun label(element: JsonElement): String =
        if (element is JsonPrimitive && element !is JsonNull) element.content else ""

    /** A saved answer's choice; throws on text that is not a JSON object. */
    fun saved(choice: String): ExamChoice {
        val choiceJson = KJson.parseObjectOrNull(choice)
        return ExamChoice(KJson.getString("text", choiceJson), KJson.getString("id", choiceJson))
    }
}
