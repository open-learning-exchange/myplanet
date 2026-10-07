package org.ole.planet.myplanet.ui.chat

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.utils.KJson

/** [ChatDetailFragment.parseModelsMap] against Gson's `fromJson(text, Map<String, String>)`, which it replaces. */
class ChatModelsMapParityTest {
    @Test
    fun `reads the stored ai models as Gson did`() {
        val stored = KJson.gsonToJson(
            buildJsonObject {
                put("openai", JsonPrimitive("gpt-4o <mini> & 'x'"))
                put("deepseek", JsonPrimitive("deepseek-chat"))
            }
        )
        val inputs = listOf(
            stored,
            "{}",
            "null",
            "   ",
            """{"a":1.50,"b":true,"c":null}""",
            """{'single':'quoted', unquoted:value}""",
            """{"a":{"x":1}}""",
            """{"a":["x"]}""",
            """["x"]""",
            "\"text\"",
            "not json",
            "{",
        )
        for (input in inputs) {
            assertEquals("models $input", gson(input), kotlinx(input))
        }
    }

    private fun gson(text: String): String = outcome {
        Gson().fromJson<Map<String, String?>>(text, object : TypeToken<Map<String, String?>>() {}.type)?.toList()
    }

    private fun kotlinx(text: String): String = outcome { ChatDetailFragment.parseModelsMap(text)?.toList() }

    private fun outcome(block: () -> Any?): String = try {
        block().toString()
    } catch (_: Exception) {
        "throws"
    }
}
