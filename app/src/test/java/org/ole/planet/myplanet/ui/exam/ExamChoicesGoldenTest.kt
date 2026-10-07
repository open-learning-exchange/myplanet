package org.ole.planet.myplanet.ui.exam

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden

/** Pins how the question screen reads a question's choices and a saved answer's choices. */
class ExamChoicesGoldenTest {
    @Test
    fun `parse reads choices as the question screen does`() {
        val inputs = listOf(
            null,
            "",
            "   ",
            "[]",
            "{}",
            "null",
            "\"text\"",
            """[{"text":"A","id":"a"},{"text":"B <b>&","id":"b"}]""",
            """["x", 1.50, 1e2, -0, true, null, {"text":1,"id":2}, ["n"], ["one", "two"], {"id":"only"}, {"text":"only"}]""",
            """[a, b c]""",
            """['single', {'text':'q','id':'w'}]""",
            "// note\n[\"commented\"]",
            ")]}'\n[\"prefixed\"]",
            "not json",
            "[1,",
            "[1] [2]",
        )
        val rows = JsonArray(
            inputs.map { input ->
                buildJsonObject {
                    put("choices", input)
                    put("parsed", outcome { render(ExamChoices.parse(input)) })
                }
            }
        )
        assertMatchesGolden("ui/exam/choices", rows)
    }

    @Test
    fun `saved reads a saved answer's choice`() {
        val inputs = listOf(
            """{"text":"A","id":"a"}""",
            """{"id":"other","text":"typed <b>"}""",
            """{"id":1.50,"text":true}""",
            """{"text":null}""",
            """{'id':'single'}""",
            "{}",
            "",
            "null",
            "x",
            "[1]",
            "{",
        )
        val rows = JsonArray(
            inputs.map { input ->
                buildJsonObject {
                    put("choice", input)
                    put("saved", outcome { render(listOf(ExamChoices.saved(input))) })
                }
            }
        )
        assertMatchesGolden("ui/exam/saved-choices", rows)
    }

    @Test
    fun `other is the object choice the screen adds`() {
        assertEquals(ExamChoice("Other", "other"), ExamChoices.OTHER)
    }

    private fun render(choices: List<ExamChoice>): JsonElement = buildJsonArray {
        for (choice in choices) {
            add(buildJsonObject {
                put("text", choice.text)
                put("id", choice.id)
            })
        }
    }

    /** A thrown exception as `throws`: the type changes with the parser, and no caller tells them apart. */
    private fun outcome(block: () -> JsonElement): JsonElement = try {
        block()
    } catch (_: Exception) {
        JsonPrimitive("<throws>")
    }
}
