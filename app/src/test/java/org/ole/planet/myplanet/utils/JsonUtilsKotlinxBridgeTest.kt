package org.ole.planet.myplanet.utils

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsKotlinxBridgeTest {
    @Test
    fun `toGson converts primitives, nulls and nested arrays into an equivalent Gson tree`() {
        val kotlinxObject = buildJsonObject {
            put("name", "value")
            put("count", 3)
            put("missing", null as String?)
            putJsonArray("tags") {
                add("a")
                add("b")
            }
        }

        val gsonObject = kotlinxObject.toGson()

        assertEquals("value", gsonObject.get("name").asString)
        assertEquals(3, gsonObject.get("count").asInt)
        assertTrue(gsonObject.get("missing").isJsonNull)
        assertEquals(2, gsonObject.getAsJsonArray("tags").size())
        assertEquals("a", gsonObject.getAsJsonArray("tags")[0].asString)
    }
}
