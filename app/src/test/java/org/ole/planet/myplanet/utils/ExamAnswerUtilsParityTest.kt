package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.ExamQuestion

/** [ExamAnswerUtils.getChoiceTextById] against the Gson reading it replaces. */
class ExamAnswerUtilsParityTest {
    @Test
    fun `maps choice ids to text as the Gson reading did`() {
        val choices = listOf(
            """[{"id":"a","text":"Apple <b>"},{"id":"b","res":"Banana"},{"id":"c","text":"","res":""},{"id":"","text":"no id"}]""",
            """[{"id":1.50,"text":"number id"},{"id":"n","text":2.50},"plain",null,["x"]]""",
            """[{'id':'s','text':'single'},{id:u,text:unquoted}]""",
            "// note\n[{\"id\":\"c\",\"text\":\"commented\"}]",
            """{"id":"a","text":"object"}""",
            "null",
            "",
            "   ",
            "not json",
            "[{",
        )
        val ids = listOf("a", "b", "c", "n", "s", "u", "", "1.50", "missing")
        for (text in choices) {
            for (id in ids) {
                val question = ExamQuestion().apply { this.choices = text }
                assertEquals("choices $text id $id", outcome { gsonChoiceTextById(text, id) }, outcome { ExamAnswerUtils.getChoiceTextById(question, id) })
            }
        }
    }

    /** The Gson implementation [ExamAnswerUtils.getChoiceTextById] had, without its cache. */
    private fun gsonChoiceTextById(choicesString: String, id: String): String {
        val map = mutableMapOf<String, String>()
        val choices = GsonUtils.getStringAsJsonArray(choicesString)
        for (i in 0 until choices.size()) {
            if (choices[i].isJsonObject) {
                val obj = choices[i].asJsonObject
                val choiceId = GsonUtils.getString("id", obj)
                val displayValue = GsonUtils.getString("text", obj).ifBlank { GsonUtils.getString("res", obj).ifBlank { null } }
                if (choiceId.isNotEmpty() && displayValue != null) map[choiceId] = displayValue
            }
        }
        return map[id] ?: id
    }

    private fun outcome(block: () -> String): String = try {
        block()
    } catch (_: Exception) {
        "throws"
    }
}
