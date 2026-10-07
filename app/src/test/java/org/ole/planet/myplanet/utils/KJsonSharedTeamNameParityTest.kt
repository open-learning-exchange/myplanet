package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.News

/**
 * [KJson.extractSharedTeamName] against the Gson [GsonUtils.extractSharedTeamName] it replaces
 * in the voices screens, on the cases the first parity test left out: the `parsedViewIn` cache,
 * numbers kept as written, and Gson's lenient grammar.
 */
class KJsonSharedTeamNameParityTest {
    @Test
    fun `reads viewIn text as Gson did`() {
        val viewIns = listOf(
            """[{"name":1.50},{}]""",
            """[{"name":1e2},{}]""",
            """[{"name":true},{}]""",
            """[{'name':'single'},{}]""",
            """[{name:unquoted},{}]""",
            "// note\n[{\"name\":\"commented\"},{}]",
            "null",
            "[",
        )
        for (viewIn in viewIns) {
            val news = News().apply { this.viewIn = viewIn }
            assertEquals("viewIn $viewIn", outcome { GsonUtils.extractSharedTeamName(news) }, outcome { KJson.extractSharedTeamName(news) })
        }
    }

    @Test
    fun `prefers the parsedViewIn cache as Gson did`() {
        val news = News().apply {
            viewIn = """[{"name":"stale"},{}]"""
            parsedViewIn = KJson.parseArrayOrNull("""[{"name":"cached 1.50"},{"section":"teams"}]""")
        }
        assertEquals("cached 1.50", GsonUtils.extractSharedTeamName(news))
        assertEquals("cached 1.50", KJson.extractSharedTeamName(news))

        val cachedNumber = News().apply { parsedViewIn = KJson.parseArrayOrNull("""[{"name":1.50},{}]""") }
        assertEquals(GsonUtils.extractSharedTeamName(cachedNumber), KJson.extractSharedTeamName(cachedNumber))
    }

    private fun outcome(block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        "throws ${e::class.java.simpleName}"
    }
}
