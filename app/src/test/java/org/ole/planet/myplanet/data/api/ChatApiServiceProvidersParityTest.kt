package org.ole.planet.myplanet.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ChatApiService.parseAiProviders] against Gson's `fromJson(text, Map<String, Boolean>)`, which it replaces. */
class ChatApiServiceProvidersParityTest {
    @Test
    fun `reads the providers body as Gson did`() {
        val inputs = listOf(
            """{"openai":true,"deepseek":false}""",
            """{"a":"true","b":"TRUE","c":"yes","d":"false"}""",
            """{'single':true, unquoted:true, bare:yes}""",
            "{}",
            "null",
            """{"a":1}""",
            """{"a":{"x":true}}""",
            """{"a":[true]}""",
            """[true]""",
            "not json",
            "{",
        )
        for (input in inputs) {
            assertEquals("providers $input", gson(input), kotlinx(input))
        }
    }

    @Test
    fun `a null value drops its provider where Gson kept it`() {
        val text = """{"openai":true,"gone":null}"""
        assertEquals(listOf("openai" to true, "gone" to null), Gson().fromJson<Map<String, Boolean?>>(text, providersType).toList())
        assertEquals(mapOf("openai" to true), ChatApiService.parseAiProviders(text))
    }

    private val providersType = object : TypeToken<Map<String, Boolean?>>() {}.type

    private fun gson(text: String): String = outcome { Gson().fromJson<Map<String, Boolean?>>(text, providersType)?.toList() }

    private fun kotlinx(text: String): String = outcome { ChatApiService.parseAiProviders(text)?.toList() }

    private fun outcome(block: () -> Any?): String = try {
        block().toString()
    } catch (_: Exception) {
        "throws"
    }
}
