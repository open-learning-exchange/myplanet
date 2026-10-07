package org.ole.planet.myplanet.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY

class KJsonBridgedNumbersTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val tree: JsonObject = buildJsonObject {
        put("s", TRICKY)
        put("numericString", "1e2")
        put("int", 7)
        put("big", BIG_LONG)
        put("float", 36.6f)
        put("double", 1.0)
        put("bool", true)
        put("null", null as String?)
        put("unquoted", JsonUnquotedLiteral("abc"))
        put("parsed", Json.parseToJsonElement("""{"n":1e2,"f":1.50,"neg":-0.0,"exp":1E21,"list":[1,2.50,true,null,"x"]}"""))
        put("array", buildJsonArray { add(JsonPrimitive(3L)); add(JsonPrimitive(0.1f)) })
    }

    @Test
    fun `is exactly toGson then toKotlinx`() {
        assertEquals(tree.toGson().toKotlinx(), tree.withGsonBridgedNumbers())
        assertEquals(tree.toGson().toKotlinx().toString(), tree.withGsonBridgedNumbers().toString())
    }

    @Test
    fun `prints what Gson printed for the bridged tree`() {
        assertEquals(tree.toGson().toString(), KJson.toGsonString(tree.withGsonBridgedNumbers()))
        assertEquals(tree.toGson().toString(), KJson.toGsonString(tree))
    }

    @Test
    fun `leaves a canonical tree unchanged`() {
        val canonical = buildJsonObject { put("a", 1); put("b", "x"); put("c", 2.5); put("d", false) }
        assertEquals(canonical.toString(), canonical.withGsonBridgedNumbers().toString())
    }
}
