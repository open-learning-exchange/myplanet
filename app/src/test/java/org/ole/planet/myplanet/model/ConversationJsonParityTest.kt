package org.ole.planet.myplanet.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/** [Conversation.listFromJsonText] against Gson's reflective `fromJson`, which it replaces. */
class ConversationJsonParityTest {
    private val inputs = listOf(
        "",
        "   ",
        "null",
        "[]",
        """[{"query":"q <b>","response":"r &"},{"query":"only"}]""",
        """[{"query":1.50,"response":true,"extra":{"x":[1]}}]""",
        """[{"query":null,"response":null}]""",
        """[{'query':'single'}, {query:unquoted}]""",
        """[{"query":"a","query":"b"}]""",
        "// note\n[{\"query\":\"commented\"}]",
        """[{"query":{"x":1}}]""",
        """[{"response":["a"]}]""",
        """[1]""",
        """["text"]""",
        """{"query":"q"}""",
        "not json",
        "[{",
    )

    @Test
    fun `reads stored conversations as Gson did`() {
        for (input in inputs) {
            assertEquals("conversations $input", gson(input), kotlinx(input))
        }
    }

    @Test
    fun `a null entry is skipped where Gson kept it`() {
        val text = """[{"query":"a"},null]"""
        assertEquals(listOf("a", null), Gson().fromJson(text, Array<Conversation>::class.java).map { it?.query })
        assertEquals(listOf("a"), Conversation.listFromJsonText(text)?.map { it.query })
    }

    private fun gson(text: String): String = outcome {
        Gson().fromJson(text, Array<Conversation>::class.java)?.map { it.query to it.response }
    }

    private fun kotlinx(text: String): String = outcome {
        Conversation.listFromJsonText(text)?.map { it.query to it.response }
    }

    private fun outcome(block: () -> Any?): String = try {
        block().toString()
    } catch (_: Exception) {
        "throws"
    }
}
